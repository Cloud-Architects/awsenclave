package solutions.cloudarchitects.awsenclave;

import com.amazonaws.encryptionsdk.AwsCrypto;
import com.amazonaws.encryptionsdk.CommitmentPolicy;
import com.amazonaws.encryptionsdk.CryptoResult;
import com.amazonaws.encryptionsdk.kms.KmsMasterKey;
import com.amazonaws.encryptionsdk.kms.KmsMasterKeyProvider;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.*;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;

public class OwnerService {
    private static final String EXAMPLE_DATA = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do " +
            "eiusmod tempor incididunt ut labore et dolore magna aliqua";

    private final KmsClient kmsClient;

    public OwnerService(KmsClient kmsClient) {
        this.kmsClient = kmsClient;
    }

    public KeyMetadata setupCrypto() {
        CreateKeyRequest createKeyRequest = CreateKeyRequest.builder()
                .build();
        CreateKeyResponse key = kmsClient.createKey(createKeyRequest);
        return key.keyMetadata();
    }

    public byte[] encryptSample(KeyMetadata keyMetadata) {

        final AwsCrypto crypto = AwsCrypto.builder()
                .withCommitmentPolicy(CommitmentPolicy.RequireEncryptRequireDecrypt)
                .build();

        final KmsMasterKeyProvider keyProvider = KmsMasterKeyProvider.builder()
                .buildStrict(keyMetadata.arn());
        final Map<String, String> encryptionContext = Collections.singletonMap("enclaveName", "aws-enclave");
        final CryptoResult<byte[], KmsMasterKey> encryptResult = crypto
                .encryptData(keyProvider, EXAMPLE_DATA.getBytes(StandardCharsets.UTF_8), encryptionContext);


        return Base64.getEncoder().encode(encryptResult.getResult());
    }
}
