package solutions.cloudarchitects.awsenclave;

import com.amazonaws.services.ec2.model.KeyPair;
import com.jcraft.jsch.Channel;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import static solutions.cloudarchitects.awsenclave.ParentSetup.EC2_USER;

public class CommandRunner {
    private static final Logger LOG = LoggerFactory.getLogger(CommandRunner.class);

    public void runCommand(KeyPair keyPair, String publicDNS, String filename) throws IOException, JSchException {
        runCommandRetry(keyPair, publicDNS, filename, 10);
    }

    private void runCommandRetry(KeyPair keyPair, String publicDNS, String filename, int retry) throws JSchException, IOException {
        try {
            executeCommand(keyPair, publicDNS, filename);
        } catch (JSchException | ConnectException connectException) {
            if(retry > 0) {
                try {
                    LOG.info("waiting to connect");
                    Thread.sleep(5_000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                runCommandRetry(keyPair, publicDNS, filename, retry - 1);
            }
        }
    }

    private void executeCommand(KeyPair keyPair, String publicDNS, String filename) throws JSchException, IOException {
        JSch jsch = new JSch();
        try {
            jsch.addIdentity(keyPair.getKeyName(), keyPair.getKeyMaterial().getBytes(StandardCharsets.UTF_8),
                    null, null);
        } catch (JSchException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
        Session session = jsch.getSession(EC2_USER, publicDNS, 22);
        Properties configuration = new Properties();
        configuration.put("StrictHostKeyChecking", "no");
        session.setConfig(configuration);

        session.connect();
        runShell(session, filename);
        session.disconnect();
    }

    private void runShell(Session session, String filename) throws JSchException, IOException {
        Channel channel = session.openChannel("shell");
        channel.setOutputStream(System.out, true);
        File initScript = new File(filename);
        FileInputStream fin = new FileInputStream(initScript);
        byte[] fileContent = new byte[(int) initScript.length()];
        fin.read(fileContent);
        channel.setInputStream(new ByteArrayInputStream(fileContent));
        channel.connect();

        while(!channel.isClosed()) {
            try {
                Thread.sleep(1_000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
