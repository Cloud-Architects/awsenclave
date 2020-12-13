package solutions.cloudarchitects.awsenclave.enclave;

import solutions.cloudarchitects.vsockj.VSock;
import solutions.cloudarchitects.vsockj.VSockAddress;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class SocketVSockProxy implements Runnable {
    private static final int BUFFER_SIZE = 4096;

    private final Socket clientSocket;
    private final int serverPort;

    public SocketVSockProxy(Socket clientSocket, int serverPort) {
        this.serverPort = serverPort;
        this.clientSocket = clientSocket;
    }

    @Override
    public void run() {
        try {
            final VSock server = new VSock(new VSockAddress(VSockAddress.VMADDR_CID_PARENT, serverPort));
            handleTrafficToServer(server);
            handleTrafficFromServer(server);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleTrafficFromServer(VSock server) throws IOException {
        final byte[] reply = new byte[BUFFER_SIZE];

        int readBytes;
        try (final InputStream inFromServer = server.getInputStream();
             final OutputStream outToClient = clientSocket.getOutputStream()) {
            while ((readBytes = inFromServer.read(reply)) != -1) {
                outToClient.write(reply, 0, readBytes);
                outToClient.flush();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                clientSocket.close();
                server.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        clientSocket.close();
    }

    private void handleTrafficToServer(VSock server) throws IOException {
        new Thread(() -> {
            final byte[] request = new byte[BUFFER_SIZE];
            int bytes_read;
            try(final OutputStream outToServer = server.getOutputStream();
                final InputStream inFromClient = clientSocket.getInputStream()) {
                while ((bytes_read = inFromClient.read(request)) != -1) {
                    outToServer.write(request, 0, bytes_read);
                }
            } catch (IOException e) {
                try {
                    clientSocket.close();
                    server.close();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
                e.printStackTrace();
            }
        }).start();
    }
}
