package solutions.cloudarchitects.awsenclave.setup;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.arn.Arn;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagement;
import com.amazonaws.services.identitymanagement.model.CreateRoleRequest;
import com.amazonaws.services.identitymanagement.model.GetRoleRequest;
import com.amazonaws.services.identitymanagement.model.PutRolePolicyRequest;
import com.amazonaws.services.identitymanagement.model.Role;
import com.amazonaws.services.kms.AWSKMS;
import com.amazonaws.services.kms.model.*;
import software.amazon.awssdk.regions.Region;
import solutions.cloudarchitects.awsenclave.setup.model.EnclaveMeasurements;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
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

    public byte[] encryptSample(String keyId) {
        ByteBuffer plaintext = ByteBuffer.wrap(EXAMPLE_DATA.getBytes(StandardCharsets.UTF_8));
        EncryptRequest req = new EncryptRequest().withKeyId(keyId).withPlaintext(plaintext);
        ByteBuffer ciphertext = kmsClient.encrypt(req).getCiphertextBlob();
        return Base64.getEncoder()
                .encode(getByteArrayFromByteBuffer(ciphertext));
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

    public void addPolicy(String keyId, EnclaveMeasurements enclaveMeasurements, String currentUserArn) {
        Role parentRole = getParentRole();
        Arn parentRoleArn = Arn.fromString(parentRole.getArn());
        String policyName = "default";
        String policy = "{" +
                "  \"Version\": \"2012-10-17\"," +
                "  \"Statement\": [{" +
                "    \"Sid\": \"Enable IAM User Permissions\"," +
                "    \"Effect\": \"Allow\"," +
                "    \"Principal\": {\"AWS\": \"arn:aws:iam::" + parentRoleArn.getAccountId() + ":root\"}," +
                "    \"Action\": [" +
                "      \"kms:*\"" +
                "    ]," +
                "    \"Resource\": \"*\"" +
                "  },{" +
                "    \"Sid\": \"Enable all IAM users key administration (EXAMPLE PURPOSES ONLY!)\"," +
                "    \"Effect\": \"Allow\"," +
                "    \"Principal\": {\"AWS\": \"" + currentUserArn + "\"}," +
                "    \"Action\": [" +
                "      \"kms:Create\"," +
                "      \"kms:Describe*\"," +
                "      \"kms:Enable\"," +
                "      \"kms:List*\"," +
                "      \"kms:Put*\"," +
                "      \"kms:Update*\"," +
                "      \"kms:Revoke*\"," +
                "      \"kms:Disable*\"," +
                "      \"kms:Get*\"," +
                "      \"kms:Delete*\"," +
                "      \"kms:TagResource\"," +
                "      \"kms:UntagResource\"," +
                "      \"kms:ScheduleKeyDeletion\"," +
                "      \"kms:CancelKeyDeletion\"" +
                "    ]," +
                "    \"Resource\": \"*\"" +
                "  },{" +
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
                "    \"Resource\": \"*\"" +
//                "    \"Resource\": \"*\"," +
//                "    \"Condition\": {" +
//                "        \"StringEqualsIgnoreCase\": {" +
//                "          \"kms:RecipientAttestation:ImageSha384\": \"" + enclaveMeasurements.getPcr0() + "\"" +
//                "        }" +
//                "    }" +
                "}]" +
                "}";

        PutKeyPolicyRequest req = new PutKeyPolicyRequest()
                .withKeyId(keyId)
                .withPolicy(policy)
                .withPolicyName(policyName);
        kmsClient.putKeyPolicy(req);
    }

    private static byte[] getByteArrayFromByteBuffer(ByteBuffer byteBuffer) {
        byte[] bytesArray = new byte[byteBuffer.remaining()];
        byteBuffer.get(bytesArray, 0, bytesArray.length);
        return bytesArray;
    }
}
