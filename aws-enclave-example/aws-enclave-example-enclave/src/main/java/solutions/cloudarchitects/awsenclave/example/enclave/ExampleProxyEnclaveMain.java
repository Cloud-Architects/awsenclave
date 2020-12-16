package solutions.cloudarchitects.awsenclave.example.enclave;

import com.amazonaws.services.kms.AWSKMS;
import com.amazonaws.services.kms.model.DecryptRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import solutions.cloudarchitects.awsenclave.enclave.EnclaveServer;
import solutions.cloudarchitects.awsenclave.example.enclave.model.Request;
import solutions.cloudarchitects.vsockj.VSock;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.Base64;

@SuppressWarnings({"InfiniteLoopStatement", "ResultOfMethodCallIgnored", "MismatchedReadAndWriteOfArray"})
public class ExampleProxyEnclaveMain {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Logger LOG = LoggerFactory.getLogger(ExampleProxyEnclaveMain.class);
    private static final int KMS_SERVER_PORT = 8443;

    public static void main(String[] args) throws IOException {
        String region = System.getenv("AWS_REGION");
        LOG.info("Region: " + region);

        InetAddress loopbackAddress = InetAddress.getLoopbackAddress();

        EnclaveServer server = new EnclaveServer(loopbackAddress);
        server.runServer(peerVSock -> {
            try {
                handleVsockRequest(region, peerVSock, server);
            } catch (IOException e) {
                LOG.warn(e.getMessage(), e);
            }
        });

        server.runProxyServer(KMS_SERVER_PORT);
    }

    private static void handleVsockRequest(String region, VSock peerVSock, EnclaveServer enclaveServer) throws IOException {
        byte[] b = new byte[8192];
        peerVSock.getInputStream().read(b, 0, 8192);
        Request request = MAPPER.readValue(b, Request.class);
        LOG.info(request.toString());
        try {
            AWSKMS kmsClient = enclaveServer.getKmsClient(request.getCredential(), region);
            byte[] decryptedSample = decryptSample(kmsClient, request);

            peerVSock.getOutputStream()
                    .write(decryptedSample);
        } catch (Exception e) {
            LOG.warn(e.getMessage(), e);
            peerVSock.getOutputStream()
                    .write(MAPPER.writeValueAsBytes(e.getMessage() + MAPPER.writeValueAsString(e.getStackTrace())));
        }
    }

    private static byte[] decryptSample(AWSKMS kmsClient, Request request) {
        DecryptRequest req = new DecryptRequest()
                .withCiphertextBlob(ByteBuffer.wrap(Base64.getDecoder().decode(request.getEncryptedText())))
                .withKeyId(request.getKeyId());
        ByteBuffer plainText = kmsClient.decrypt(req).getPlaintext();

        return plainText.array();
    }
}
