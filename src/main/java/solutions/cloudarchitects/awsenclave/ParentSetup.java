package solutions.cloudarchitects.awsenclave;

import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcraft.jsch.JSchException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Collections;

public class ParentSetup {
    public static final String EC2_USER = "ec2-user";

    private static final String DEFAULT_KEY_NAME = "awsenclave";
    private static final String DEFAULT_SECURITY_GROUP_NAME = "JavaSecurityGroup";
    private static final String SSM_RESOLVE_LATEST_AMAZON_LINUX_2 =
            "resolve:ssm:/aws/service/ami-amazon-linux-latest/amzn2-ami-hvm-x86_64-gp2";
    private static final String HOST_PRIVATE_KEY_NAME = "host_key_pair.json";
    private static final String DEFAULT_SUBNET_ID = "subnet-0c7e359db7c0be7fd"; // auto assign public IP: true
    private static final String filename1 = "basic_commands1.sh";
    private static final String filename2 = "basic_commands2.sh";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Logger LOG = LoggerFactory.getLogger(CommandRunner.class);

    public void setupParent() {
        AmazonEC2 amazonEC2Client = AmazonEC2ClientBuilder.defaultClient();

        KeyPair keyPair = loadKey(amazonEC2Client);
        String securityGroupName = getSecurityGroupName(amazonEC2Client);

        RunInstancesRequest runInstancesRequest =
                new RunInstancesRequest()
                        .withImageId(SSM_RESOLVE_LATEST_AMAZON_LINUX_2)
                        .withInstanceType(InstanceType.C5Xlarge)
                        .withMinCount(1)
                        .withMaxCount(1)
                        .withKeyName(keyPair.getKeyName())
                        .withSubnetId(DEFAULT_SUBNET_ID)
                        .withEnclaveOptions(new EnclaveOptionsRequest()
                                .withEnabled(true));

        LOG.info("creating an instance with an enclave");
        RunInstancesResult result = amazonEC2Client.runInstances(
                runInstancesRequest);

        try {
            Thread.sleep(5_000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        String createdInstanceId = result.getReservation().getInstances().get(0)
                .getInstanceId();

        String publicDNS = "";
        String publicIP = "";
        for (Reservation reservation : amazonEC2Client.describeInstances().getReservations()) {
            if (reservation.getInstances().get(0).getPrivateIpAddress() != null &&
                    reservation.getInstances().get(0).getInstanceId().equals(createdInstanceId)) {
                publicDNS = reservation.getInstances().get(0).getPublicDnsName();
                publicIP = reservation.getInstances().get(0).getPublicIpAddress();
                LOG.info("Public DNS: " + publicDNS);
                LOG.info("Public IP: " + publicIP);
            }
        }

        createShellScript();

        CommandRunner commandRunner = new CommandRunner();
        try {
            LOG.info("waiting for basic setup");
            commandRunner.runCommand(keyPair, publicDNS, filename1);
            LOG.info("running enclave");
            commandRunner.runCommand(keyPair, publicDNS, filename2);
        } catch (JSchException | IOException e) {
            e.printStackTrace();
        } finally {
            TerminateInstancesRequest tir = new TerminateInstancesRequest(Collections.singletonList(createdInstanceId));
            LOG.info("terminating instance: " + createdInstanceId);
            amazonEC2Client.terminateInstances(tir);
        }
    }


    private String getSecurityGroupName(AmazonEC2 amazonEC2Client) {
        try {
            amazonEC2Client.describeSecurityGroups(new DescribeSecurityGroupsRequest()
                    .withGroupNames(DEFAULT_SECURITY_GROUP_NAME));
            return DEFAULT_SECURITY_GROUP_NAME;

        } catch (AmazonEC2Exception exception) {
            CreateSecurityGroupRequest csgr = new CreateSecurityGroupRequest();
            csgr.withGroupName(DEFAULT_SECURITY_GROUP_NAME).withDescription("created with awsenclave");

            amazonEC2Client.createSecurityGroup(csgr);

            IpPermission ipPermission = new IpPermission();
            ipPermission.withIpv4Ranges(Collections.singletonList(new IpRange().withCidrIp("0.0.0.0/0")))
                    .withIpProtocol("tcp")
                    .withFromPort(22)
                    .withToPort(22);

            AuthorizeSecurityGroupIngressRequest authorizeSecurityGroupIngressRequest =
                    new AuthorizeSecurityGroupIngressRequest();

            authorizeSecurityGroupIngressRequest.withGroupName("JavaSecurityGroup")
                    .withIpPermissions(ipPermission);

            amazonEC2Client.authorizeSecurityGroupIngress(authorizeSecurityGroupIngressRequest);

            return DEFAULT_SECURITY_GROUP_NAME;
        }
    }

    private KeyPair loadKey(AmazonEC2 amazonEC2Client) {
        try {
            return MAPPER.readValue(new File(HOST_PRIVATE_KEY_NAME), KeyPair.class);
        } catch (IOException e) {
            CreateKeyPairRequest createKeyPairRequest = new CreateKeyPairRequest();
            createKeyPairRequest.withKeyName(DEFAULT_KEY_NAME);
            CreateKeyPairResult createKeyPairResult =
                    amazonEC2Client.createKeyPair(createKeyPairRequest);

            KeyPair keyPair = createKeyPairResult.getKeyPair();
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(HOST_PRIVATE_KEY_NAME))) {
                writer.write(MAPPER.writeValueAsString(keyPair));
            } catch (IOException ioException) {
                throw new IllegalStateException(ioException.getMessage(), ioException);
            }
            return keyPair;
        }
    }

    private void createShellScript() {
        try {
            PrintStream out = new PrintStream(new FileOutputStream(new File(filename1)));
            out.println("echo \"Programmatically SSHed into the instance.\"");
            out.println("sudo amazon-linux-extras enable aws-nitro-enclaves-cli");
            out.println("sudo amazon-linux-extras enable docker");
            out.println("sudo yum install docker aws-nitro-enclaves-cli aws-nitro-enclaves-cli-devel -y");
            out.println("sudo usermod -aG ne " + EC2_USER);
            out.println("sudo usermod -aG docker " + EC2_USER);
            out.println("echo \"vm.nr_hugepages=1536\" | sudo tee /etc/sysctl.d/99-nitro.conf; sudo sysctl -p /etc/sysctl.d/99-nitro.conf");
            out.println("sudo reboot");
            out.println("exit");
            out.close();

            out = new PrintStream(new FileOutputStream(new File(filename2)));
            out.println("echo \"vm.nr_hugepages=1536\" | sudo tee /etc/sysctl.d/99-nitro.conf; sudo sysctl -p /etc/sysctl.d/99-nitro.conf");
            out.println("sudo grep Huge /proc/meminfo");

            out.println("nitro-cli --version");
            out.println("sudo systemctl start nitro-enclaves-allocator.service && sudo systemctl enable nitro-enclaves-allocator.service");
            out.println("sudo systemctl start docker && sudo systemctl enable docker");

            out.println("touch Dockerfile");
            out.println("echo \"FROM epahomov/docker-spark:lightweighted\n\" >> Dockerfile");
            out.println("docker build . -t enclave-image:latest");

            out.println("nitro-cli build-enclave --docker-uri enclave-image:latest  --output-file sample.eif");
            out.println("nitro-cli run-enclave --cpu-count 2 --memory 3072 --eif-path sample.eif --enclave-cid 10");
            out.println("nitro-cli console --enclave-id 10");
            out.println("exit");
            out.close();
        } catch (Exception e) {
            LOG.warn(e.getMessage(), e);
        }
    }
}
