package solutions.cloudarchitects.awsenclave.setup;

import com.amazonaws.services.identitymanagement.AmazonIdentityManagement;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient;
import com.amazonaws.services.kms.AWSKMS;
import com.amazonaws.services.kms.AWSKMSClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
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
        OwnerService ownerService = new OwnerService(kmsClient, iamClient, region);
        ParentAdministratorService parentAdministratorService =
                new ParentAdministratorService(ec2Client, iamClient, ownerService, region);

        KeyPair keyPair = parentAdministratorService.loadKey();
        Ec2Instance ec2Instance = parentAdministratorService.createParent(keyPair);
        try {
            parentAdministratorService.prepareSampleDockerImage(keyPair, ec2Instance);
            EnclaveMeasurements enclaveMeasurements = parentAdministratorService.buildEnclave(keyPair, ec2Instance);

            String enclaveId = parentAdministratorService.runEnclave(keyPair, ec2Instance);
            parentAdministratorService.runVSockProxy(keyPair, ec2Instance,
                    String.format("kms.%s.amazonaws.com", region));
            parentAdministratorService.runHost(keyPair, ec2Instance, enclaveId);

//            // TODO: update key policy to add measurement attributes
            String keyId = ownerService.getKeyId();
//            byte[] bytes = ownerService.encryptSample(keyId);
//            // TODO: store sample data
//
//            ownerService.addPolicy(keyId, enclaveMeasurements);
        } finally {
//            TerminateInstancesRequest tir = TerminateInstancesRequest.builder()
//                    .instanceIds(ec2Instance.getInstanceId())
//                    .build();
//            LOG.info("terminating instance: " + ec2Instance);
//            ec2Client.terminateInstances(tir);
        }
    }
}
