package solutions.cloudarchitects.awsenclave.setup;

import com.amazonaws.encryptionsdk.AwsCrypto;
import com.amazonaws.encryptionsdk.CommitmentPolicy;
import com.amazonaws.encryptionsdk.CryptoResult;
import com.amazonaws.encryptionsdk.kms.KmsMasterKey;
import com.amazonaws.encryptionsdk.kms.KmsMasterKeyProvider;
import com.amazonaws.services.kms.AWSKMS;
import com.amazonaws.services.kms.model.*;
import software.amazon.awssdk.regions.Region;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

public class OwnerService {
    private static final String EXAMPLE_DATA = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do " +
            "eiusmod tempor incididunt ut labore et dolore magna aliqua";

    private final AWSKMS kmsClient;
    private final Region region;

    public OwnerService(AWSKMS kmsClient, Region region) {
        this.kmsClient = kmsClient;
        this.region = region;
    }

    public String setupCrypto() {
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
}
