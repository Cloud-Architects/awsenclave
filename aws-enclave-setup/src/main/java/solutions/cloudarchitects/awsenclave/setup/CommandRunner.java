package solutions.cloudarchitects.awsenclave.setup;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import solutions.cloudarchitects.awsenclave.setup.model.KeyPair;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Properties;

import static solutions.cloudarchitects.awsenclave.setup.ParentAdministratorService.EC2_USER;

public class CommandRunner {
    private static final Logger LOG = LoggerFactory.getLogger(CommandRunner.class);

    public Optional<String> runCommand(KeyPair keyPair, String publicDNS, String script, boolean returnOutput)
            throws IOException, JSchException {
        return runCommandRetry(keyPair, publicDNS, script, returnOutput, 10);
    }

    private Optional<String> runCommandRetry(KeyPair keyPair, String publicDNS, String script, boolean returnOutput, int retry)
            throws JSchException, UnsupportedEncodingException {
        try {
            return executeCommand(keyPair, publicDNS, script, returnOutput);
        } catch (JSchException connectException) {
            if (retry > 0) {
                try {
                    LOG.info("waiting to connect");
                    Thread.sleep(5_000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                return runCommandRetry(keyPair, publicDNS, script, returnOutput, retry - 1);
            }
            throw new IllegalStateException("exceeded max retries");
        }
    }

    private Optional<String> executeCommand(KeyPair keyPair, String publicDNS, String script, boolean returnOutput)
            throws JSchException, UnsupportedEncodingException {
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
        Optional<String> result = runShell(session, script, returnOutput);
        session.disconnect();

        return result;
    }

    private Optional<String> runShell(Session session, String script, boolean returnOutput)
            throws JSchException, UnsupportedEncodingException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Channel channel = session.openChannel("shell");
        if (returnOutput) {
            channel.setOutputStream(baos);
        } else {
            channel.setOutputStream(System.out, true);
        }
        channel.setInputStream(new ByteArrayInputStream(script.getBytes(StandardCharsets.UTF_8)));
        channel.connect();

        while (!channel.isClosed()) {
            try {
                Thread.sleep(1_000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        if (returnOutput) {
            return Optional.of(baos.toString("UTF-8"));
        } else {
            return Optional.empty();
        }
    }
}
