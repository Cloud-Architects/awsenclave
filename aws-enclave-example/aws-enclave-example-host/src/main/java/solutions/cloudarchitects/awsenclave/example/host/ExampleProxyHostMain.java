package solutions.cloudarchitects.awsenclave.example.host;

import com.amazonaws.util.EC2MetadataUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import solutions.cloudarchitects.awsenclave.example.host.model.Request;
import solutions.cloudarchitects.vsockj.VSock;
import solutions.cloudarchitects.vsockj.VSockAddress;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

public class ExampleProxyHostMain {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Logger LOG = LoggerFactory.getLogger(ExampleProxyHostMain.class);

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            throw new IllegalArgumentException("Pass 2 arguments with CID of the enclave and encrypted text");
        }
        int enclave_cid = Integer.parseInt(args[0]);
        String encryptedText = args[1];

        try (VSock client = new VSock(new VSockAddress(enclave_cid, 5000))) {

            Optional<Map.Entry<String, EC2MetadataUtils.IAMSecurityCredential>> credentialOptional =
                    EC2MetadataUtils.getIAMSecurityCredentials().entrySet()
                    .stream().findFirst();
            if (!credentialOptional.isPresent()) {
                throw new IllegalStateException("No associated instance profile to a host");
            }
            EC2MetadataUtils.IAMSecurityCredential credential = credentialOptional.get().getValue();
            client.getOutputStream()
                    .write(MAPPER.writeValueAsBytes(new Request(encryptedText, credential)));
            byte[] b = new byte[8192];
            client.getInputStream().read(b, 0, 8192);
            LOG.info("Received: " + new String(b, StandardCharsets.UTF_8));
        }
    }
}
