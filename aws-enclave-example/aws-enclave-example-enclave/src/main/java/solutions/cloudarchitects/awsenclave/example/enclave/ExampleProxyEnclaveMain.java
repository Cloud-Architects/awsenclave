package solutions.cloudarchitects.awsenclave.example.enclave;

import com.amazonaws.AmazonWebServiceRequest;
import com.amazonaws.ClientConfiguration;
import com.amazonaws.SystemDefaultDnsResolver;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.encryptionsdk.AwsCrypto;
import com.amazonaws.encryptionsdk.CommitmentPolicy;
import com.amazonaws.encryptionsdk.CryptoResult;
import com.amazonaws.encryptionsdk.kms.KmsMasterKey;
import com.amazonaws.encryptionsdk.kms.KmsMasterKeyProvider;
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
import solutions.cloudarchitects.awsenclave.example.enclave.model.Request;
import solutions.cloudarchitects.vsockj.ServerVSock;
import solutions.cloudarchitects.vsockj.VSock;
import solutions.cloudarchitects.vsockj.VSockAddress;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;

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
                    byte[] b = new byte[8192];
                    peerVSock.getInputStream().read(b, 0, 8192);
                    Request request = MAPPER.readValue(b, Request.class);

                    try {
                        AWSKMSClientBuilder clientBuilder = getClientBuilder(loopbackAddress, request);
                        byte[] decryptedSample = decryptSample(clientBuilder, request);

                        peerVSock.getOutputStream()
                                .write(decryptedSample);
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

    private static AWSKMSClientBuilder getClientBuilder(InetAddress loopbackAddress, Request request) {
        return AWSKMSClientBuilder.standard()
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
                        new BasicSessionCredentials(request.getCredential().accessKeyId,
                                request.getCredential().secretAccessKey, request.getCredential().token)));
    }

    private static byte[] decryptSample(AWSKMSClientBuilder clientBuilder, Request request) {
        final AwsCrypto crypto = AwsCrypto.builder()
                .withCommitmentPolicy(CommitmentPolicy.RequireEncryptRequireDecrypt)
                .build();

        final KmsMasterKeyProvider keyProvider = KmsMasterKeyProvider.builder()
                .withDefaultRegion(AWS_REGION)
                .withClientBuilder(clientBuilder)
                .buildStrict(request.getKeyId());
        final Map<String, String> encryptionContext = Collections.singletonMap("enclaveName", "aws-enclave");
        final CryptoResult<byte[], KmsMasterKey> decryptResult = crypto
                .decryptData(keyProvider, Base64.getDecoder().decode(request.getEncryptedText()));

        return decryptResult.getResult();
    }
}
