package solutions.cloudarchitects.awsenclave;

import com.amazonaws.services.kms.AWSKMS;
import com.amazonaws.services.kms.AWSKMSClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.TerminateInstancesRequest;
import solutions.cloudarchitects.awsenclave.model.Ec2Instance;
import solutions.cloudarchitects.awsenclave.model.EnclaveMeasurements;
import solutions.cloudarchitects.awsenclave.model.KeyPair;

class Main {
    private static final Logger LOG = LoggerFactory.getLogger(CommandRunner.class);

    public static void main(String... args) {
        Ec2Client ec2Client = Ec2Client.create();
        AWSKMS kmsClient = AWSKMSClientBuilder.defaultClient();
        ParentAdministratorService parentAdministratorService = new ParentAdministratorService(ec2Client);
        OwnerService ownerService = new OwnerService(kmsClient);

        KeyPair keyPair = parentAdministratorService.loadKey();
        Ec2Instance ec2Instance = parentAdministratorService.createParent(keyPair);
        try {
            parentAdministratorService.prepareSampleEnclave(keyPair, ec2Instance);
            EnclaveMeasurements enclaveMeasurements = parentAdministratorService.buildEnclave(keyPair, ec2Instance);
            // TODO: update key policy to add measurement attributes
            String keyId = ownerService.setupCrypto();
            byte[] bytes = ownerService.encryptSample(keyId);
            // TODO: store sample data
            parentAdministratorService.runEnclave(keyPair, ec2Instance);
        } finally {
            TerminateInstancesRequest tir = TerminateInstancesRequest.builder()
                    .instanceIds(ec2Instance.getInstanceId())
                    .build();
            LOG.info("terminating instance: " + ec2Instance);
            ec2Client.terminateInstances(tir);
        }
    }
}
