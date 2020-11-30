package solutions.cloudarchitects.awsenclave;

import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.KeyPair;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import solutions.cloudarchitects.awsenclave.model.Ec2Instance;

import java.util.Collections;

class Main {
    private static final Logger LOG = LoggerFactory.getLogger(CommandRunner.class);

    public static void main(String... args) {
        AmazonEC2 amazonEC2Client = AmazonEC2ClientBuilder.defaultClient();
        ParentSetup parentSetup = new ParentSetup(amazonEC2Client);

        KeyPair keyPair = parentSetup.loadKey();
        Ec2Instance ec2Instance = parentSetup.createParent(keyPair);
        try {
            parentSetup.setupParent(keyPair, ec2Instance);
            parentSetup.runSampleEnclave(keyPair, ec2Instance);

        } finally {
            TerminateInstancesRequest tir = new TerminateInstancesRequest(
                    Collections.singletonList(ec2Instance.getInstanceId()));
            LOG.info("terminating instance: " + ec2Instance);
            amazonEC2Client.terminateInstances(tir);
        }
    }
}
