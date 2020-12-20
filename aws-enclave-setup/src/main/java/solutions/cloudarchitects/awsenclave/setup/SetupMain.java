package solutions.cloudarchitects.awsenclave.setup;

import com.amazonaws.services.identitymanagement.AmazonIdentityManagement;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient;
import com.amazonaws.services.kms.AWSKMS;
import com.amazonaws.services.kms.AWSKMSClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.TerminateInstancesRequest;
import software.amazon.awssdk.services.sts.StsClient;
import solutions.cloudarchitects.awsenclave.setup.model.Ec2Instance;
import solutions.cloudarchitects.awsenclave.setup.model.EnclaveMeasurements;
import solutions.cloudarchitects.awsenclave.setup.model.KeyPair;

class SetupMain {
    private static final Logger LOG = LoggerFactory.getLogger(CommandRunner.class);

    public static void main(String... args) {
        Ec2Client ec2Client = Ec2Client.create();
        AmazonIdentityManagement iamClient = AmazonIdentityManagementClient.builder().build();
        Region region = Region.of(
                ec2Client.describeAvailabilityZones().availabilityZones().get(0).regionName()
        );
        AWSKMS kmsClient = AWSKMSClientBuilder.standard()
                .withRegion(region.id())
                .build();
        StsClient stsClient = StsClient.create();
        String currentUserArn = stsClient.getCallerIdentity().arn();

        OwnerService ownerService = new OwnerService(kmsClient, iamClient, region);
        ParentAdministratorService parentAdministratorService =
                new ParentAdministratorService(ec2Client, iamClient, ownerService, region);

        KeyPair keyPair = parentAdministratorService.loadKey();
        Ec2Instance ec2Instance = parentAdministratorService.createParent(keyPair);
        try {
            parentAdministratorService.prepareSampleDockerImage(keyPair, ec2Instance, region);
            EnclaveMeasurements enclaveMeasurements = parentAdministratorService.buildEnclave(keyPair, ec2Instance);

            String enclaveId = parentAdministratorService.runEnclave(keyPair, ec2Instance);
            parentAdministratorService.runVSockProxy(keyPair, ec2Instance,
                    String.format("kms.%s.amazonaws.com", region));

            String keyId = ownerService.getKeyId();
            byte[] bytes = ownerService.encryptSample(keyId);
            ownerService.addPolicy(keyId, enclaveMeasurements, currentUserArn);

            parentAdministratorService.runHost(keyPair, ec2Instance, enclaveId, bytes, keyId);
            LOG.info("Instance ID: " + ec2Instance.getInstanceId());
            LOG.info("Public DNS: " + ec2Instance.getDomainAddress());
        } finally {
//            TerminateInstancesRequest tir = TerminateInstancesRequest.builder()
//                    .instanceIds(ec2Instance.getInstanceId())
//                    .build();
//            LOG.info("terminating instance: " + ec2Instance);
//            ec2Clientient.terminateInstances(tir);
        }
    }
}
