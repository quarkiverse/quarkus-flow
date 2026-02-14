package io.quarkiverse.flow.it;

import java.io.IOException;
import java.net.ServerSocket;

public class HttpPortUtils {

    public static int generateRandomPort() throws IOException {
        return findRandomPort();
    }

    private static int findRandomPort() throws IOException {
        for (int i = 0; i < 100; i++) {
            int port = 1024 + (int) (Math.random() * 64512); // 1024 to 65535
            if (isPortAvailable(port)) {
                return port;
            }
        }
        throw new IOException("No available port found");
    }

    private static boolean isPortAvailable(int port) {
        try (ServerSocket socket = new ServerSocket(port)) {
            socket.setReuseAddress(true);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
