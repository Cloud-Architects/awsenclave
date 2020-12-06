package solutions.cloudarchitects.awsenclave;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.TerminateInstancesRequest;
import solutions.cloudarchitects.awsenclave.model.Ec2Instance;
import solutions.cloudarchitects.awsenclave.model.KeyPair;

class Main {
    private static final Logger LOG = LoggerFactory.getLogger(CommandRunner.class);

    public static void main(String... args) {
        Ec2Client ec2Client = Ec2Client.create();
        ParentSetup parentSetup = new ParentSetup(ec2Client);

        KeyPair keyPair = parentSetup.loadKey();
        Ec2Instance ec2Instance = parentSetup.createParent(keyPair);
        try {
            parentSetup.runSampleEnclave(keyPair, ec2Instance);
        } finally {
            TerminateInstancesRequest tir = TerminateInstancesRequest.builder()
                    .instanceIds(ec2Instance.getInstanceId())
                    .build();
            LOG.info("terminating instance: " + ec2Instance);
            ec2Client.terminateInstances(tir);
        }
    }
}
