package solutions.cloudarchitects.awsenclave.example.enclave.model;

import com.amazonaws.util.EC2MetadataUtils;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Request {
    public final String encryptedText;
    public final EC2MetadataUtils.IAMSecurityCredential credential;

    public Request(@JsonProperty("encryptedText") String encryptedText,
                   @JsonProperty("credential") EC2MetadataUtils.IAMSecurityCredential credential) {
        this.encryptedText = encryptedText;
        this.credential = credential;
    }

    public String getEncryptedText() {
        return encryptedText;
    }

    public EC2MetadataUtils.IAMSecurityCredential getCredential() {
        return credential;
    }
}
