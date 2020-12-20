package solutions.cloudarchitects.awsenclave.example.enclave.model;

import com.amazonaws.util.EC2MetadataUtils;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Request {
    private final String encryptedText;
    private final String keyId;
    private final EC2MetadataUtils.IAMSecurityCredential credential;

    public Request(@JsonProperty("encryptedText") String encryptedText,
                   @JsonProperty("keyId") String keyId,
                   @JsonProperty("credential") EC2MetadataUtils.IAMSecurityCredential credential) {
        this.encryptedText = encryptedText;
        this.keyId = keyId;
        this.credential = credential;
    }

    public String getKeyId() {
        return keyId;
    }

    public String getEncryptedText() {
        return encryptedText;
    }

    public EC2MetadataUtils.IAMSecurityCredential getCredential() {
        return credential;
    }

    @Override
    public String toString() {
        return "Request{" +
                "encryptedText='" + encryptedText + '\'' +
                ", keyId='" + keyId + '\'' +
                ", credential=" + credential +
                '}';
    }
}
