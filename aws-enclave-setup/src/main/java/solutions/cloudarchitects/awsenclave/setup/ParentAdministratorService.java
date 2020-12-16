package solutions.cloudarchitects.awsenclave.setup;

import com.amazonaws.services.identitymanagement.AmazonIdentityManagement;
import com.amazonaws.services.identitymanagement.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcraft.jsch.JSchException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;
import solutions.cloudarchitects.awsenclave.setup.model.Ec2Instance;
import solutions.cloudarchitects.awsenclave.setup.model.EnclaveMeasurements;
import solutions.cloudarchitects.awsenclave.setup.model.KeyPair;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.Optional;

public final class ParentAdministratorService {
    public static final String EC2_USER = "ec2-user";

    private static final String DEFAULT_KEY_NAME = "awsenclave";
    private static final String DEFAULT_SECURITY_GROUP_NAME = "JavaSecurityGroup";
    private static final String SSM_RESOLVE_LATEST_AMAZON_LINUX_2 =
            "resolve:ssm:/aws/service/ami-amazon-linux-latest/amzn2-ami-hvm-x86_64-gp2";
    private static final String HOST_PRIVATE_KEY_NAME = "host_key_pair.json";
    private static final String ENCLAVE_PARENT_PROFILE = "enclaveParentProfile";
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
        String[] setupScript = {
                "sudo amazon-linux-extras enable aws-nitro-enclaves-cli",
                "sudo amazon-linux-extras enable docker",
                "sudo yum install docker aws-nitro-enclaves-cli aws-nitro-enclaves-cli-devel -y",
                "sudo usermod -aG ne " + EC2_USER,
                "sudo usermod -aG docker " + EC2_USER,
                "echo \"vm.nr_hugepages=1536\" | sudo tee /etc/sysctl.d/99-nitro.conf; sudo sysctl -p /etc/sysctl.d/99-nitro.conf",
                "sudo reboot"
        };
        try {
            LOG.info("waiting for parent setup");
            commandRunner.runCommand(keyPair, domainAddress, setupScript, false);
        } catch (JSchException | IOException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    public void prepareSampleDockerImage(KeyPair keyPair, Ec2Instance ec2Instance, Region region) {
        String[] setupScript = {
                "nitro-cli --version",
                "sudo systemctl start nitro-enclaves-allocator.service && sudo systemctl enable nitro-enclaves-allocator.service",
                "sudo systemctl start docker && sudo systemctl enable docker",
                "sudo amazon-linux-extras enable corretto8",
                "sudo yum install java-1.8.0-amazon-corretto-devel git -y",

                "git clone https://github.com/Cloud-Architects/awsenclave",
                "cd awsenclave",
                "./mvnw -Dmaven.artifact.threads=30 install",
                "./mvnw -f aws-enclave-example/aws-enclave-example-enclave/pom.xml -Dmaven.artifact.threads=30 clean nar:nar-download nar:nar-unpack package jib:dockerBuild",
                "sed -i 's/ENCLAVE_REGION/" + region.id() + "/g' deploy/enclave-proxy/Dockerfile\n",
                "docker build deploy/enclave-proxy -t aws-enclave-example-enclave"
        };

        try {
            LOG.info("building Docker image");
            commandRunner.runCommand(keyPair, ec2Instance.getDomainAddress(), setupScript, false);
        } catch (JSchException | IOException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    public EnclaveMeasurements buildEnclave(KeyPair keyPair, Ec2Instance ec2Instance) {
        String[] setupScript = {
                "nitro-cli build-enclave --docker-uri aws-enclave-example-enclave:latest --output-file sample.eif"
        };
        try {
            LOG.info("waiting to build an enclave");
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
        String[] setupScript = {
                "echo 'vm.nr_hugepages=1536' | sudo tee /etc/sysctl.d/99-nitro.conf; sudo sysctl -p /etc/sysctl.d/99-nitro.conf",
                String.format("nitro-cli run-enclave --cpu-count 2 --memory 3072 --eif-path sample.eif --enclave-cid %s --debug-mode", enclaveId),
                "nitro-cli describe-enclaves",
        };
        try {
            LOG.info("waiting to run an enclave");
            commandRunner.runCommand(keyPair, ec2Instance.getDomainAddress(), setupScript, false);
            return enclaveId;
        } catch (JSchException | IOException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    public Ec2Instance createParent(KeyPair keyPair) {
        Optional<String> imageId = NitroEnclavesDeveloperAmi.getImageId(region);
        InstanceProfile parentInstanceProfile = getParentInstanceProfile();
        String securityGroupName = getSecurityGroupId();

        RunInstancesRequest runInstancesRequest = RunInstancesRequest.builder()
                .instanceType(InstanceType.C5_XLARGE)
                .iamInstanceProfile(IamInstanceProfileSpecification.builder()
                        .name(parentInstanceProfile.getInstanceProfileName())
                        .build())
                .minCount(1)
                .maxCount(1)
                .keyName(keyPair.getKeyName())
                .networkInterfaces(InstanceNetworkInterfaceSpecification.builder()
                        .deviceIndex(0)
                        .associatePublicIpAddress(true)
                        .groups(securityGroupName)
                        .build())
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
                    .withInstanceProfileName(ENCLAVE_PARENT_PROFILE)).getInstanceProfile();
            if (parentProfile.getRoles().size() == 0) {
                iamClient.deleteInstanceProfile(new DeleteInstanceProfileRequest()
                        .withInstanceProfileName(ENCLAVE_PARENT_PROFILE));
                throw Ec2Exception.builder().build();
            }
        } catch (NoSuchEntityException ex) {
            parentProfile = iamClient.createInstanceProfile(new CreateInstanceProfileRequest()
                    .withInstanceProfileName(ENCLAVE_PARENT_PROFILE)).getInstanceProfile();

            iamClient.addRoleToInstanceProfile(new AddRoleToInstanceProfileRequest()
                    .withInstanceProfileName(parentProfile.getInstanceProfileName())
                    .withRoleName(role.getRoleName()));
        }

        return parentProfile;
    }

    private String getSecurityGroupId() {
        try {
            DescribeSecurityGroupsResponse groupsResponse = amazonEC2Client.describeSecurityGroups(
                    DescribeSecurityGroupsRequest.builder()
                            .groupNames(DEFAULT_SECURITY_GROUP_NAME)
                            .build());
            return groupsResponse.securityGroups().get(0).groupId();

        } catch (Ec2Exception exception) {
            CreateSecurityGroupRequest csgr = CreateSecurityGroupRequest.builder()
                    .groupName(DEFAULT_SECURITY_GROUP_NAME)
                    .description("created with awsenclave")
                    .build();

            CreateSecurityGroupResponse groupResponse = amazonEC2Client.createSecurityGroup(csgr);

            IpPermission ipPermission = IpPermission.builder()
                    .ipRanges(Collections.singletonList(IpRange.builder().cidrIp("0.0.0.0/0").build()))
                    .ipProtocol("tcp")
                    .fromPort(22)
                    .toPort(22)
                    .build();

            AuthorizeSecurityGroupIngressRequest authorizeSecurityGroupIngressRequest =
                    AuthorizeSecurityGroupIngressRequest.builder()
                            .groupName(DEFAULT_SECURITY_GROUP_NAME)
                            .ipPermissions(ipPermission)
                            .build();

            amazonEC2Client.authorizeSecurityGroupIngress(authorizeSecurityGroupIngressRequest);

            return groupResponse.groupId();
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
        String[] setupScript = {String.format("screen -d -m vsock-proxy 8443 %s 443", domain)};
        try {
            LOG.info("running vsock proxy");
            commandRunner.runCommand(keyPair, ec2Instance.getDomainAddress(), setupScript, false);
        } catch (JSchException | IOException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    public void runHost(KeyPair keyPair, Ec2Instance ec2Instance, String enclaveCid, byte[] bytes, String keyId) {
        String encodedEncryptedText = new String(bytes, StandardCharsets.UTF_8);
        String[] setupScript = {
                "cd awsenclave",
                String.format("./mvnw -f aws-enclave-example/aws-enclave-example-host/pom.xml compile exec:exec " +
                        "-Denclave.cid=%s -Dencrypted.text=%s -Dkey.id=%s", enclaveCid, encodedEncryptedText, keyId)
        };
        try {
            LOG.info("running host");
            commandRunner.runCommand(keyPair, ec2Instance.getDomainAddress(), setupScript, false);
        } catch (JSchException | IOException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }
}
