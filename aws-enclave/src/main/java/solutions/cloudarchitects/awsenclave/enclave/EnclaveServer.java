package solutions.cloudarchitects.awsenclave.enclave;

import com.amazonaws.AmazonWebServiceRequest;
import com.amazonaws.ClientConfiguration;
import com.amazonaws.SystemDefaultDnsResolver;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.handlers.RequestHandler2;
import com.amazonaws.services.kms.AWSKMS;
import com.amazonaws.services.kms.AWSKMSClientBuilder;
import com.amazonaws.util.EC2MetadataUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import solutions.cloudarchitects.awsenclave.enclave.model.request.DescribePCRRequest;
import solutions.cloudarchitects.awsenclave.enclave.model.response.DescribePCRResponse;
import solutions.cloudarchitects.vsockj.ServerVSock;
import solutions.cloudarchitects.vsockj.VSock;
import solutions.cloudarchitects.vsockj.VSockAddress;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

@SuppressWarnings("InfiniteLoopStatement")
public class EnclaveServer implements Closeable {
    private static final Logger LOG = LoggerFactory.getLogger(EnclaveServer.class);

    private final InetAddress localServerAddress;
    private final NsmDevice nsmDevice;
    private ServerSocket serverSocket;

    public EnclaveServer(InetAddress localServerAddress, NsmDevice nsmDevice) {
        this.localServerAddress = localServerAddress;
        this.nsmDevice = nsmDevice;
    }

    public void runServer(Consumer<VSock> requestConsumer) {
        nsmDevice.initialize();
        new Thread(() -> {
            try {
                this.runServerThreaded(requestConsumer);
            } catch (IOException e) {
                LOG.warn(e.getMessage(), e);
            }
        }).start();
    }

    public void runProxyServer(int port) throws IOException {
        serverSocket = new ServerSocket(port, 50, localServerAddress);
        new Thread(() -> {
            try {
                LOG.info(String.format("Running proxy server on %s:%s", localServerAddress, serverSocket.getLocalPort()));
                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    new Thread(new SocketVSockProxy(clientSocket, port)).start();
                }
            } catch (IOException e) {
                LOG.warn(e.getMessage(), e);
            }
        }).start();
    }

    private void runServerThreaded(Consumer<VSock> requestConsumer) throws IOException {
        ServerVSock server = new ServerVSock();
        server.bind(new VSockAddress(VSockAddress.VMADDR_CID_ANY, 5000));
        LOG.info("Bound on Cid: " + server.getLocalCid());

        try {
            while (true) {
                try (VSock peerVSock = server.accept()) {
                    requestConsumer.accept(peerVSock);
                } catch (Exception e) {
                    LOG.warn(e.getMessage(), e);
                }
            }
        } finally {
            server.close();
        }
    }

    public String getPcr0() {
        DescribePCRResponse describePCRResponse = nsmDevice.describePCR(new DescribePCRRequest((short) 0));
        String pcr0 = new String(describePCRResponse.getData(), StandardCharsets.UTF_8);
        return pcr0;
    }

    public AWSKMS getKmsClient(EC2MetadataUtils.IAMSecurityCredential credential, String region) {
        return AWSKMSClientBuilder.standard()
                .withClientConfiguration(new ClientConfiguration()
                        .withDnsResolver(new SystemDefaultDnsResolver() {
                            @Override
                            public InetAddress[] resolve(String host) throws UnknownHostException {
                                if (String.format("kms.%s.amazonaws.com", region).equals(host)) {
                                    return new InetAddress[]{localServerAddress}; // for host redirection
                                } else {
                                    return super.resolve(host);
                                }
                            }
                        }))
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(
                        String.format("kms.%s.amazonaws.com:8443", region), region // for port redirection
                ))
                .withRequestHandlers(new RequestHandler2() {
                    @Override
                    public AmazonWebServiceRequest beforeExecution(AmazonWebServiceRequest request) {
                        return super.beforeExecution(request);
                    }
                })
                .withCredentials(new AWSStaticCredentialsProvider(
                        new BasicSessionCredentials(credential.accessKeyId, credential.secretAccessKey,
                                credential.token)))
                .build();
    }

    @Override
    public void close() throws IOException {
        nsmDevice.close();
        if (serverSocket != null) {
            serverSocket.close();
        }
    }
}
