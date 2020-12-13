package solutions.cloudarchitects.awsenclave.example.enclave;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.SystemDefaultDnsResolver;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.kms.AWSKMS;
import com.amazonaws.services.kms.AWSKMSClientBuilder;
import com.amazonaws.services.kms.model.AliasListEntry;
import com.amazonaws.services.kms.model.DescribeKeyRequest;
import com.amazonaws.services.kms.model.DescribeKeyResult;
import com.amazonaws.util.EC2MetadataUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import solutions.cloudarchitects.awsenclave.enclave.SocketVSockProxy;
import solutions.cloudarchitects.vsockj.ServerVSock;
import solutions.cloudarchitects.vsockj.VSock;
import solutions.cloudarchitects.vsockj.VSockAddress;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;

public class ExampleEnclaveMain {
    private static final String AWS_REGION = "ap-southeast-1";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Logger LOG = LoggerFactory.getLogger(ExampleEnclaveMain.class);

    public static void main(String[] args) throws IOException {
        final String[] proxyExceptionMessage = {"None"};
        ServerSocket serverSocket = new ServerSocket(8433);
        InetAddress serverAddress = serverSocket.getInetAddress();
        new Thread(() -> {
            try {
                LOG.info(String.format("Running proxy server on %s:%s", serverAddress, serverSocket.getLocalPort()));
                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    new Thread(new SocketVSockProxy(clientSocket, 8433)).start();
                }
            } catch (IOException e) {
                LOG.warn(e.getMessage(), e);
                proxyExceptionMessage[0] = e.getMessage();
            }
        }).start();

        ServerVSock server = new ServerVSock();
        server.bind(new VSockAddress(VSockAddress.VMADDR_CID_ANY, 5000));
        LOG.info("Bound on Cid: " + server.getLocalCid());

        try {
            while (true) {
                try (VSock peerVSock = server.accept()) {
                    byte[] b = new byte[4096];
                    peerVSock.getInputStream().read(b, 0, 4096);
                    EC2MetadataUtils.IAMSecurityCredential credential = MAPPER
                            .readValue(b, EC2MetadataUtils.IAMSecurityCredential.class);

                    try {
                        AWSKMS kmsClient = AWSKMSClientBuilder.standard()
                                .withClientConfiguration(new ClientConfiguration()
                                        .withDnsResolver(new SystemDefaultDnsResolver() {
                                            @Override
                                            public InetAddress[] resolve(String host) throws UnknownHostException {
                                                if ("kms.ap-southeast-1.amazonaws.com".equals(host)) {
                                                    return new InetAddress[]{serverAddress}; // for host redirection
                                                } else {
                                                    return super.resolve(host);
                                                }
                                            }
                                        }))
                                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(
                                        "kms.ap-southeast-1.amazonaws.com:8433", AWS_REGION // for port redirection
                                ))
                                .withCredentials(new AWSStaticCredentialsProvider(
                                        new BasicSessionCredentials(credential.accessKeyId, credential.secretAccessKey, credential.token)))
                                .build();

                        String enclaveKeyId = kmsClient.listAliases().getAliases().stream()
                                .filter(alias -> alias.getAliasName().equals("alias/enclave"))
                                .map(AliasListEntry::getTargetKeyId)
                                .findAny().get();

                        DescribeKeyResult describeKeyResult = kmsClient.describeKey(new DescribeKeyRequest()
                                .withKeyId(enclaveKeyId));

                        peerVSock.getOutputStream()
                                .write(MAPPER.writeValueAsBytes(describeKeyResult));
                    } catch (Exception e) {
                        LOG.warn(e.getMessage(), e);
                        peerVSock.getOutputStream()
                                .write(MAPPER.writeValueAsBytes(proxyExceptionMessage[0] + e.getMessage()
                                        + MAPPER.writeValueAsString(e.getStackTrace())));
                    }

                } catch (Exception e) {
                    LOG.warn(e.getMessage(), e);
                }
            }
        } finally {
            server.close();
        }
    }

    public static String execCmd(String cmd) {
        StringBuilder result = new StringBuilder();
        try {
            ProcessBuilder builder = new ProcessBuilder(cmd);
            builder.redirectErrorStream(true);
            Process process = builder.start();
            InputStream is = process.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));

            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line).append("\n");
            }
        } catch (Exception e) {
            LOG.warn(e.getMessage(), e);
        }

        return result.toString();
    }
}
