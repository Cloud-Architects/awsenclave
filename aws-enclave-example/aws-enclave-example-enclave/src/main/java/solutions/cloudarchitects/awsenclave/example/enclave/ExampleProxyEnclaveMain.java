package solutions.cloudarchitects.awsenclave.example.enclave;

import com.amazonaws.AmazonWebServiceRequest;
import com.amazonaws.ClientConfiguration;
import com.amazonaws.SystemDefaultDnsResolver;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.handlers.RequestHandler2;
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

import java.io.*;
import java.net.*;

@SuppressWarnings({"InfiniteLoopStatement", "ResultOfMethodCallIgnored", "MismatchedReadAndWriteOfArray"})
public class ExampleProxyEnclaveMain {
    private static final String AWS_REGION = "ap-southeast-1";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Logger LOG = LoggerFactory.getLogger(ExampleProxyEnclaveMain.class);

    public static void main(String[] args) throws IOException {
        final String[] proxyExceptionMessage = {"None"};
        InetAddress loopbackAddress = InetAddress.getLoopbackAddress();
        ServerSocket serverSocket = new ServerSocket(8443, 50, loopbackAddress);
        new Thread(() -> {
            try {
                LOG.info(String.format("Running proxy server on %s:%s", loopbackAddress, serverSocket.getLocalPort()));
                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    new Thread(new SocketVSockProxy(clientSocket, 8443)).start();
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
                                                    return new InetAddress[]{loopbackAddress}; // for host redirection
                                                } else {
                                                    return super.resolve(host);
                                                }
                                            }
                                        }))
                                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(
                                        "kms.ap-southeast-1.amazonaws.com:8443", AWS_REGION // for port redirection
                                ))
                                .withRequestHandlers(new RequestHandler2() {
                                    @Override
                                    public AmazonWebServiceRequest beforeExecution(AmazonWebServiceRequest request) {
                                        return super.beforeExecution(request);
                                    }
                                })
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
}