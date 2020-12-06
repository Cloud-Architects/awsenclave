package solutions.cloudarchitects.awsenclave;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcraft.jsch.JSchException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;
import solutions.cloudarchitects.awsenclave.model.Ec2Instance;
import solutions.cloudarchitects.awsenclave.model.EnclaveMeasurements;
import solutions.cloudarchitects.awsenclave.model.KeyPair;

import java.io.*;
import java.util.Collections;
import java.util.Optional;
import java.util.regex.Pattern;

public class ParentAdministratorService {
    public static final String EC2_USER = "ec2-user";

    private static final String DEFAULT_KEY_NAME = "awsenclave";
    private static final String DEFAULT_SECURITY_GROUP_NAME = "JavaSecurityGroup";
    private static final String SSM_RESOLVE_LATEST_AMAZON_LINUX_2 =
            "resolve:ssm:/aws/service/ami-amazon-linux-latest/amzn2-ami-hvm-x86_64-gp2";
    private static final String HOST_PRIVATE_KEY_NAME = "host_key_pair.json";
    private static final String DEFAULT_SUBNET_ID = "subnet-0c7e359db7c0be7fd"; // auto assign public IP: true
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Logger LOG = LoggerFactory.getLogger(CommandRunner.class);

    private final Ec2Client amazonEC2Client;
    private final CommandRunner commandRunner;

    public ParentAdministratorService(Ec2Client amazonEC2Client) {
        this(amazonEC2Client, new CommandRunner());
    }

    public ParentAdministratorService(Ec2Client amazonEC2Client, CommandRunner commandRunner) {
        this.amazonEC2Client = amazonEC2Client;
        this.commandRunner = commandRunner;
    }

    private void setupParent(KeyPair keyPair, String domainAddress) {
        String setupScript = "sudo amazon-linux-extras enable aws-nitro-enclaves-cli\n" +
                "sudo amazon-linux-extras enable docker\n" +
                "sudo yum install docker aws-nitro-enclaves-cli aws-nitro-enclaves-cli-devel -y\n" +
                "sudo usermod -aG ne " + EC2_USER + "\n" +
                "sudo usermod -aG docker " + EC2_USER + "\n" +
                "echo \"vm.nr_hugepages=1536\" | sudo tee /etc/sysctl.d/99-nitro.conf; sudo sysctl -p /etc/sysctl.d/99-nitro.conf\n" +
                "sudo reboot\n" +
                "exit\n";
        try {
            LOG.info("waiting for basic setup");
            commandRunner.runCommand(keyPair, domainAddress, setupScript, false);
        } catch (JSchException | IOException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    public void prepareSampleEnclave(KeyPair keyPair, Ec2Instance ec2Instance) {
        String setupScript = "nitro-cli --version\n" +
                "sudo systemctl start nitro-enclaves-allocator.service && sudo systemctl enable nitro-enclaves-allocator.service\n" +
                "sudo systemctl start docker && sudo systemctl enable docker\n" +

                "touch run.sh\n" +
                "echo '#!/bin/sh' >> run.sh\n" +
                "echo 'python3 /app/client.py' >> run.sh\n" +

                "touch server.py\n" +
                "echo 'import socket' >> server.py\n" +
                "echo 'client_socket = socket.socket(socket.AF_VSOCK, socket.SOCK_STREAM)' >> server.py\n" +
                "echo 'cid = socket.VMADDR_CID_ANY' >> server.py\n" +
                "echo 'client_port = 5000' >> server.py\n" +
                "echo 'client_socket.bind((cid, client_port))' >> server.py\n" +
                "echo 'client_socket.listen()' >> server.py\n" +
                "echo 'while True:' >> server.py\n" +
                "echo '       try:' >> server.py\n" +
                "echo '               (conn, (remote_cid, remote_port)) = client_socket.accept()' >> server.py\n" +
                "echo '               req = conn.recv(4096)' >> server.py\n" +
                "echo '               print(req)' >> server.py\n" +
                "echo '               conn.sendall(b\"Got: \" + req)' >> server.py\n" +
                "echo '               conn.close()' >> server.py\n" +
                "echo '       except:' >> server.py\n" +
                "echo '               print(\"An exception occurred\")' >> server.py\n" +

                "touch client.py\n" +
                "echo 'import socket' >> client.py\n" +
                "echo 'client_socket = socket.socket(socket.AF_VSOCK, socket.SOCK_STREAM)' >> client.py\n" +
                "echo 'cid = socket.VMADDR_CID_HOST' >> client.py\n" +
                "echo 'client_port = 5000' >> client.py\n" +
                "echo 'client_socket.connect((cid, client_port))' >> client.py\n" +
                "echo 'client_socket.send(b\"hello world\")' >> client.py\n" +
                "echo 'response = client_socket.recv(65536)' >> client.py\n" +
                "echo 'client_socket.close()' >> client.py\n" +

                "touch Dockerfile\n" +
                "echo 'FROM amazonlinux' >> Dockerfile\n" +
                "echo 'RUN yum install python3 net-tools -y' >> Dockerfile\n" +
                "echo 'WORKDIR /app' >> Dockerfile\n" +
                "echo 'COPY server.py ./' >> Dockerfile\n" +
                "echo 'COPY client.py ./' >> Dockerfile\n" +
                "echo 'COPY run.sh ./' >> Dockerfile\n" +
                "echo 'RUN chmod +x run.sh' >> Dockerfile\n" +
                "echo 'CMD /app/run.sh' >> Dockerfile\n" +

                "docker build . -t enclave-image:latest\n" +
                "exit\n";

        try {
            LOG.info("running enclave");
            commandRunner.runCommand(keyPair, ec2Instance.getDomainAddress(), setupScript, false);
        } catch (JSchException | IOException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    public EnclaveMeasurements buildEnclave(KeyPair keyPair, Ec2Instance ec2Instance) {
        String setupScript = "nitro-cli build-enclave --docker-uri enclave-image:latest  --output-file sample.eif\n" +
                "exit\n";
        try {
            LOG.info("waiting for basic setup");
            Optional<String> result = commandRunner
                    .runCommand(keyPair, ec2Instance.getDomainAddress(), setupScript, true);
            String logLines = result.orElseThrow(() -> new IllegalStateException("No enclave measurements"));
            return EnclaveMeasurements.fromBuild(logLines);
        } catch (JSchException | IOException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    public void runEnclave(KeyPair keyPair, Ec2Instance ec2Instance) {
        String setupScript =
                "echo 'vm.nr_hugepages=1536' | sudo tee /etc/sysctl.d/99-nitro.conf; sudo sysctl -p /etc/sysctl.d/99-nitro.conf\n" +
                "sudo grep Huge /proc/meminfo\n" +
                "nitro-cli run-enclave --cpu-count 2 --memory 3072 --eif-path sample.eif --enclave-cid 10\n" +
                "nitro-cli describe-enclaves\n" +
                "exit\n";
        try {
            LOG.info("waiting for basic setup");
            commandRunner.runCommand(keyPair, ec2Instance.getDomainAddress(), setupScript, false);
        } catch (JSchException | IOException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    public Ec2Instance createParent(KeyPair keyPair) {
        String securityGroupName = getSecurityGroupName();

        Region region = getRegion();
        Optional<String> imageId = NitroEnclavesDeveloperAmi.getImageId(region);
        RunInstancesRequest runInstancesRequest = RunInstancesRequest.builder()
                .instanceType(InstanceType.C5_XLARGE)
                .minCount(1)
                .maxCount(1)
                .keyName(keyPair.getKeyName())
                .subnetId(DEFAULT_SUBNET_ID)
                .enclaveOptions(EnclaveOptionsRequest.builder()
                        .enabled(true)
                        .build())
                .imageId(imageId.orElse(SSM_RESOLVE_LATEST_AMAZON_LINUX_2))
                .build();

        LOG.info("creating an instance with an enclave");
        RunInstancesResponse result = amazonEC2Client.runInstances(
                runInstancesRequest);
        try {
            Thread.sleep(5_000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        String createdInstanceId = result.instances().get(0)
                .instanceId();

        String publicDNS = "";
        String publicIP = "";
        for (Reservation reservation : amazonEC2Client.describeInstances().reservations()) {
            if (reservation.instances().get(0).privateIpAddress() != null &&
                    reservation.instances().get(0).instanceId().equals(createdInstanceId)) {
                publicDNS = reservation.instances().get(0).publicDnsName();
                publicIP = reservation.instances().get(0).publicIpAddress();
                LOG.info("Public DNS: " + publicDNS);
                LOG.info("Public IP: " + publicIP);
            }
        }

        if (!imageId.isPresent()) {
            LOG.info("Using region without prebuilt AMI, setting up development env manually, MAY contain errors");
            setupParent(keyPair, publicDNS);
        }

        return new Ec2Instance(createdInstanceId, publicDNS);
    }

    private String getSecurityGroupName() {
        try {
            amazonEC2Client.describeSecurityGroups(DescribeSecurityGroupsRequest.builder()
                    .groupNames(DEFAULT_SECURITY_GROUP_NAME)
                    .build());
            return DEFAULT_SECURITY_GROUP_NAME;

        } catch (Ec2Exception exception) {
            CreateSecurityGroupRequest csgr = CreateSecurityGroupRequest.builder()
                    .groupName(DEFAULT_SECURITY_GROUP_NAME)
                    .description("created with awsenclave")
                    .build();

            amazonEC2Client.createSecurityGroup(csgr);

            IpPermission ipPermission = IpPermission.builder()
                .ipRanges(Collections.singletonList(IpRange.builder().cidrIp("0.0.0.0/0").build()))
                    .ipProtocol("tcp")
                    .fromPort(22)
                    .toPort(22)
                    .build();

            AuthorizeSecurityGroupIngressRequest authorizeSecurityGroupIngressRequest =
                    AuthorizeSecurityGroupIngressRequest.builder()
                            .groupName("JavaSecurityGroup")
                            .ipPermissions(ipPermission)
                            .build();

            amazonEC2Client.authorizeSecurityGroupIngress(authorizeSecurityGroupIngressRequest);

            return DEFAULT_SECURITY_GROUP_NAME;
        }
    }

    public KeyPair loadKey() {
        try {
            return MAPPER.readValue(new File(HOST_PRIVATE_KEY_NAME), KeyPair.class);
        } catch (IOException e) {
            CreateKeyPairRequest createKeyPairRequest = CreateKeyPairRequest.builder()
                    .keyName(DEFAULT_KEY_NAME)
                    .build();
            CreateKeyPairResponse createKeyPairResponse = amazonEC2Client.createKeyPair(createKeyPairRequest);
            KeyPair keyPair = new KeyPair(createKeyPairResponse.keyMaterial(), createKeyPairResponse.keyName());
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(HOST_PRIVATE_KEY_NAME))) {
                writer.write(MAPPER.writeValueAsString(keyPair));
            } catch (IOException ioException) {
                throw new IllegalStateException(ioException.getMessage(), ioException);
            }
            return keyPair;
        }
    }

    private Region getRegion() {
        amazonEC2Client.describeAvailabilityZones().availabilityZones().get(0).regionName();
        return Region.of(
                amazonEC2Client.describeAvailabilityZones().availabilityZones().get(0).regionName()
        );
    }
}
