package solutions.cloudarchitects.awsenclave.example;

import solutions.cloudarchitects.vsockj.VSock;
import solutions.cloudarchitects.vsockj.VSockAddress;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Pass one argument with CID of the enclave");
        }
        int enclave_cid = Integer.parseInt(args[0]);

        try (VSock client = new VSock(new VSockAddress(enclave_cid, 5000))) {
            client.getOutputStream()
                    .write("Hello world\n".getBytes(StandardCharsets.UTF_8));
            byte[] b = new byte[4096];
            client.getInputStream().read(b, 0, 4096);
            System.out.println(new String(b, StandardCharsets.UTF_8));
        }
    }
}
