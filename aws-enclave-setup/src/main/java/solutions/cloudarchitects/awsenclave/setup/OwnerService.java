package solutions.cloudarchitects.awsenclave.setup;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.encryptionsdk.AwsCrypto;
import com.amazonaws.encryptionsdk.CommitmentPolicy;
import com.amazonaws.encryptionsdk.CryptoResult;
import com.amazonaws.encryptionsdk.kms.KmsMasterKey;
import com.amazonaws.encryptionsdk.kms.KmsMasterKeyProvider;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagement;
import com.amazonaws.services.identitymanagement.model.CreateRoleRequest;
import com.amazonaws.services.identitymanagement.model.GetRoleRequest;
import com.amazonaws.services.identitymanagement.model.PutRolePolicyRequest;
import com.amazonaws.services.identitymanagement.model.Role;
import com.amazonaws.services.kms.AWSKMS;
import com.amazonaws.services.kms.model.*;
import software.amazon.awssdk.regions.Region;
import solutions.cloudarchitects.awsenclave.setup.model.EnclaveMeasurements;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

public final class OwnerService {
    private static final String EXAMPLE_DATA = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do " +
            "eiusmod tempor incididunt ut labore et dolore magna aliqua";

    private final AWSKMS kmsClient;
    private final AmazonIdentityManagement iamClient;
    private final Region region;

    public OwnerService(AWSKMS kmsClient, AmazonIdentityManagement iamClient, Region region) {
        this.kmsClient = kmsClient;
        this.iamClient = iamClient;
        this.region = region;
    }

    public String getKeyId() {
        ListAliasesResult listAliasesResult = kmsClient.listAliases();
        Optional<String> enclaveKeyId = listAliasesResult.getAliases().stream()
                .filter(alias -> alias.getAliasName().equals("alias/enclave"))
                .map(AliasListEntry::getTargetKeyId)
                .findAny();
        if (enclaveKeyId.isPresent()) {
            return enclaveKeyId.get();
        }
        CreateKeyRequest createKeyRequest = new CreateKeyRequest();
        CreateKeyResult key = kmsClient.createKey(createKeyRequest);
        kmsClient.createAlias(new CreateAliasRequest()
                .withTargetKeyId(key.getKeyMetadata().getKeyId())
                .withAliasName("alias/enclave"));
        return key.getKeyMetadata().getKeyId();
    }

    private KeyMetadata getKeyMetadata() {
        return kmsClient.describeKey(new DescribeKeyRequest()
                .withKeyId(getKeyId())).getKeyMetadata();
    }

    public byte[] encryptSample(String keyId) {
        final AwsCrypto crypto = AwsCrypto.builder()
                .withCommitmentPolicy(CommitmentPolicy.RequireEncryptRequireDecrypt)
                .build();

        final KmsMasterKeyProvider keyProvider = KmsMasterKeyProvider.builder()
                .withDefaultRegion(region.id())
                .buildStrict(keyId);
        final Map<String, String> encryptionContext = Collections.singletonMap("enclaveName", "aws-enclave");
        final CryptoResult<byte[], KmsMasterKey> encryptResult = crypto
                .encryptData(keyProvider, EXAMPLE_DATA.getBytes(StandardCharsets.UTF_8), encryptionContext);


        return Base64.getEncoder().encode(encryptResult.getResult());
    }

    public Role getParentRole() {
        Role role;
        try {
            role = iamClient.getRole(new GetRoleRequest().withRoleName("enclaveParentRole")).getRole();
        } catch (AmazonServiceException ex) {
            role = iamClient.createRole(new CreateRoleRequest()
                    .withRoleName("enclaveParentRole")
                    .withAssumeRolePolicyDocument("{" +
                            "    \"Version\": \"2012-10-17\"," +
                            "    \"Statement\": [" +
                            "        {" +
                            "            \"Sid\": \"\"," +
                            "            \"Effect\": \"Allow\"," +
                            "            \"Principal\": {" +
                            "                \"Service\": \"ec2.amazonaws.com\"" +
                            "            }," +
                            "            \"Action\": \"sts:AssumeRole\"" +
                            "        }" +
                            "    ]" +
                            "}")).getRole();
            iamClient.putRolePolicy(new PutRolePolicyRequest()
                    .withRoleName(role.getRoleName())
                    .withPolicyName("allowListingAliases")
                    .withPolicyDocument(
                            "{" +
                                    "  \"Version\": \"2012-10-17\"," +
                                    "  \"Statement\": [" +
                                    "    {" +
                                    "        \"Effect\": \"Allow\"," +
                                    "        \"Action\": \"kms:listAliases\"," +
                                    "        \"Resource\": \"*\"" +
                                    "    }," +
                                    "    {" +
                                    "        \"Effect\": \"Allow\"," +
                                    "        \"Action\": \"kms:DescribeKey\"," +
                                    "        \"Resource\": \"*\"" +
                                    "    }" +
                                    "   ]" +
                                    "}"
                    ));
        }
        return role;
    }

    public void addPolicy(String keyId, EnclaveMeasurements enclaveMeasurements) {
        Role parentRole = getParentRole();
        String policyName = "default";
        String policy = "{" +
                "  \"Version\": \"2012-10-17\"," +
                "  \"Statement\": [{" +
                "    \"Sid\": \"Allow encryption for Enclave Host\"," +
                "    \"Effect\": \"Allow\"," +
                "    \"Principal\": {\"AWS\": \"" + parentRole.getArn() + "\"}," +
                "    \"Action\": [" +
                "      \"kms:Encrypt\"," +
                "      \"kms:GenerateDataKey*\"," +
                "      \"kms:DescribeKey\"," +
                "      \"kms:ReEncrypt*\"" +
                "    ]," +
                "    \"Resource\": \"*\"" +
                "  },{" +
                "    \"Sid\": \"Enable decrypt from enclave\"," +
                "    \"Effect\": \"Allow\"," +
                "    \"Principal\": {\"AWS\": \"" + parentRole.getArn() + "\"}," +
                "    \"Action\": [" +
                "      \"kms:Decrypt\"" +
                "    ]," +
                "    \"Resource\": \"*\"," +
                "    \"Condition\": {" +
                "        \"StringEqualsIgnoreCase\": {" +
                "          \"kms:RecipientAttestation:ImageSha384\": \"" + enclaveMeasurements.getPcr0() + "\"" +
                "        }" +
                "    }" +
                "}]" +
                "}";

        PutKeyPolicyRequest req = new PutKeyPolicyRequest()
                .withKeyId(keyId)
                .withPolicy(policy)
                .withPolicyName(policyName);
        kmsClient.putKeyPolicy(req);
    }
}
