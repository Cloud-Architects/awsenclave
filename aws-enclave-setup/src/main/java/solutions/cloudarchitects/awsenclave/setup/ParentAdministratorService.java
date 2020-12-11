package solutions.cloudarchitects.awsenclave.setup;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.encryptionsdk.exception.AwsCryptoException;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagement;
import com.amazonaws.services.identitymanagement.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcraft.jsch.JSchException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;
import solutions.cloudarchitects.awsenclave.setup.model.Ec2Instance;
import solutions.cloudarchitects.awsenclave.setup.model.EnclaveMeasurements;
import solutions.cloudarchitects.awsenclave.setup.model.KeyPair;

import java.io.*;
import java.util.Collections;
import java.util.Optional;

public final class ParentAdministratorService {
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
    private final AmazonIdentityManagement iamClient;
    private final OwnerService ownerService;
    private final CommandRunner commandRunner;
    private final Region region;

    public ParentAdministratorService(Ec2Client amazonEC2Client, AmazonIdentityManagement iamClient,
                                      OwnerService ownerService, Region region) {
        this(amazonEC2Client, iamClient, ownerService, region, new CommandRunner());
    }

    public ParentAdministratorService(Ec2Client amazonEC2Client, AmazonIdentityManagement iamClient,
                                      OwnerService ownerService, Region region, CommandRunner commandRunner) {
        this.amazonEC2Client = amazonEC2Client;
        this.iamClient = iamClient;
        this.ownerService = ownerService;
        this.region = region;
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
                "sudo amazon-linux-extras enable corretto8\n" +
                "sudo yum install java-1.8.0-amazon-corretto-devel git -y\n" +

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

                "git clone https://github.com/Cloud-Architects/awsenclave\n" +
                "cd awsenclave\n" +
                "./mvnw -f aws-enclave-example/aws-enclave-example-enclave/pom.xml -Dmaven.artifact.threads=30 clean nar:nar-download nar:nar-unpack package jib:dockerBuild\n" +
                "exit\n";

        try {
            LOG.info("running enclave");
            commandRunner.runCommand(keyPair, ec2Instance.getDomainAddress(), setupScript, false);
        } catch (JSchException | IOException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    public EnclaveMeasurements buildEnclave(KeyPair keyPair, Ec2Instance ec2Instance) {
        String setupScript = "nitro-cli build-enclave --docker-uri aws-enclave-example-enclave:latest --output-file sample.eif\n" +
                "exit\n";
        try {
            LOG.info("waiting for basic setup");
            Optional<String> result = commandRunner
                    .runCommand(keyPair, ec2Instance.getDomainAddress(), setupScript, true);
            String logLines = result.orElseThrow(() -> new IllegalStateException("No enclave measurements"));
            LOG.info(logLines);
            return EnclaveMeasurements.fromBuild(logLines);
        } catch (JSchException | IOException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    public String runEnclave(KeyPair keyPair, Ec2Instance ec2Instance) {
        String enclaveId = "10";
        String setupScript =
                "echo 'vm.nr_hugepages=1536' | sudo tee /etc/sysctl.d/99-nitro.conf; sudo sysctl -p /etc/sysctl.d/99-nitro.conf\n" +
                        "sudo grep Huge /proc/meminfo\n" +
                        String.format("nitro-cli run-enclave --cpu-count 2 --memory 3072 --eif-path sample.eif --enclave-cid %s \n", enclaveId) +
                        "nitro-cli describe-enclaves\n" +
                        "exit\n";
        try {
            LOG.info("waiting for basic setup");
            commandRunner.runCommand(keyPair, ec2Instance.getDomainAddress(), setupScript, false);
            return enclaveId;
        } catch (JSchException | IOException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    public Ec2Instance createParent(KeyPair keyPair) {
        String securityGroupName = getSecurityGroupName();
        Optional<String> imageId = NitroEnclavesDeveloperAmi.getImageId(region);
        InstanceProfile parentInstanceProfile = getParentInstanceProfile();

        RunInstancesRequest runInstancesRequest = RunInstancesRequest.builder()
                .instanceType(InstanceType.C5_XLARGE)
                .iamInstanceProfile(IamInstanceProfileSpecification.builder()
                        .name(parentInstanceProfile.getInstanceProfileName())
                        .build())
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

    private InstanceProfile getParentInstanceProfile() {
        Role role = ownerService.getParentRole();

        InstanceProfile parentProfile;
        try {
            parentProfile = iamClient.getInstanceProfile(new GetInstanceProfileRequest()
                    .withInstanceProfileName("enclaveParentProfile")).getInstanceProfile();
        } catch (AmazonServiceException ex) {
            parentProfile = iamClient.createInstanceProfile(new CreateInstanceProfileRequest()
                .withInstanceProfileName("enclaveParentProfile")).getInstanceProfile();

            iamClient.addRoleToInstanceProfile(new AddRoleToInstanceProfileRequest()
                .withInstanceProfileName(parentProfile.getInstanceProfileName())
                .withRoleName(role.getRoleName()));
        }

        return parentProfile;
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

    public void runVSockProxy(KeyPair keyPair, Ec2Instance ec2Instance, String domain) {
        String setupScript =
                "cd awsenclave\n" +
                        String.format("nohup vsock-proxy 8000 %s 443 &\n", domain) +
                        "exit\n";
        try {
            LOG.info("waiting to run client");
            commandRunner.runCommand(keyPair, ec2Instance.getDomainAddress(), setupScript, false);
        } catch (JSchException | IOException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    public void runHost(KeyPair keyPair, Ec2Instance ec2Instance, String enclaveCid) {
        String setupScript =
                "cd awsenclave\n" +
                        String.format("./mvnw -f aws-enclave-example/aws-enclave-example-host/pom.xml -Dmaven.artifact.threads=30 compile exec:exec -Denclave.cid=%s\n", enclaveCid) +
                        "exit\n";
        try {
            LOG.info("waiting to run client");
            commandRunner.runCommand(keyPair, ec2Instance.getDomainAddress(), setupScript, false);
        } catch (JSchException | IOException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }
}
