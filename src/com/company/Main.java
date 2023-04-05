package com.company;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class Main {

    public static void main(String[] args) throws Exception {
        Server server = new Server();
        server.start(new ServerSocket(6666)).join();
    }

    public static class Server {
        private final Map<SocketAddress, SocketMeta> map = new HashMap<>();
        public CompletableFuture<Void> start(ServerSocket serverSocket) {
            return CompletableFuture.runAsync(() -> {
                while (true) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        EchoClientHandler echoClientHandler = new EchoClientHandler(map, clientSocket);
                        new Thread(echoClientHandler).start();
                    } catch (Exception e) {
                        System.out.println(e);
                    }
                }
            });
        }
    }


    private static class EchoClientHandler implements Runnable {
        private final Map<SocketAddress, SocketMeta> map;
        private final Socket clientSocket;
        private Integer deviceId;
        private SocketAddress remoteSocketAddress;

        public EchoClientHandler(Map<SocketAddress, SocketMeta> map, Socket clientSocket) {
            this.map = map;
            this.clientSocket = clientSocket;
        }

        public void run() {
            try (PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
                 BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                 clientSocket) {

                while (clientSocket.isConnected()) {
                    String line = in.readLine();
                    if (line.startsWith("Connection_id:")) {
                        deviceId = Integer.parseInt(line.split(" ")[1]);
                        remoteSocketAddress = clientSocket.getRemoteSocketAddress();
                        map.put(remoteSocketAddress, new SocketMeta(clientSocket, out, in, deviceId));
                    } else {
                        final Optional<SocketMeta> connection = map.entrySet().stream()
                                .filter(e -> !e.getKey().equals(remoteSocketAddress))
                                .map(Map.Entry::getValue)
                                .filter(s -> s.getDeviceId().equals(deviceId))
                                .findFirst();
                        connection.ifPresent(socketMeta -> socketMeta.out.println(line));
                    }
                }
            } catch (Exception e) {
                System.out.println(e);
            }
        }
    }

    public static class SocketMeta {
        private Socket clientSocket;
        private PrintWriter out;
        private BufferedReader in;
        private Integer deviceId;

        public SocketMeta(Socket clientSocket, PrintWriter out, BufferedReader in, Integer deviceId) {
            this.clientSocket = clientSocket;
            this.out = out;
            this.in = in;
            this.deviceId = deviceId;
        }

        public Integer getDeviceId() {
            return deviceId;
        }
    }
}