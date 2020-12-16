package solutions.cloudarchitects.awsenclave.setup;

import com.jcraft.jsch.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import solutions.cloudarchitects.awsenclave.setup.model.KeyPair;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import java.util.Properties;

import static solutions.cloudarchitects.awsenclave.setup.ParentAdministratorService.EC2_USER;

public class CommandRunner {
    private static final Logger LOG = LoggerFactory.getLogger(CommandRunner.class);

    public Optional<String> runCommand(KeyPair keyPair, String publicDNS, String[] script, boolean returnOutput)
            throws IOException, JSchException {
        String command = String.join("; ", script);
        return runCommandRetry(keyPair, publicDNS, command, returnOutput, 10);
    }

    private Optional<String> runCommandRetry(KeyPair keyPair, String publicDNS, String command, boolean returnOutput, int retry)
            throws JSchException {
        try {
            return executeCommand(keyPair, publicDNS, command, returnOutput);
        } catch (JSchException | IOException connectException) {
            if (retry > 0) {
                try {
                    LOG.info("waiting to connect");
                    Thread.sleep(5_000);
                } catch (InterruptedException e) {
                    LOG.warn(e.getMessage(), e);
                }
                return runCommandRetry(keyPair, publicDNS, command, returnOutput, retry - 1);
            }
            throw new IllegalStateException("exceeded max retries");
        }
    }

    private Optional<String> executeCommand(KeyPair keyPair, String publicDNS, String command, boolean returnOutput)
            throws JSchException, IOException {
        LOG.info("Attempting to run command: " + command);
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
        Optional<String> result = runExec(session, command, returnOutput);
        session.disconnect();

        return result;
    }

    private Optional<String> runExec(Session session, String command, boolean returnOutput)
            throws JSchException, IOException {
        Channel channel = session.openChannel("exec");
        ((ChannelExec)channel).setCommand(command);
        StringBuilder outputBuffer = new StringBuilder();

        ((ChannelExec) channel).setErrStream(System.err);
        InputStream commandOutput = channel.getInputStream();
        channel.connect();
        int readByte = commandOutput.read();

        while(readByte != 0xffffffff) {
            if (returnOutput) {
                outputBuffer.append((char)readByte);
            } else {
                System.out.print((char)readByte);
            }
            readByte = commandOutput.read();
        }

        if (returnOutput) {
            return Optional.of(outputBuffer.toString());
        } else {
            return Optional.empty();
        }
    }
}
