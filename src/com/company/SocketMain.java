package com.company;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

public class SocketMain {

    //ffplay -> service (TCP) -> nginx_rtmp
    //ffplay -> service (TCP) <- dvr || establish connection
    //ffplay <-> service (rtmp) <-> dvr || traffic
    //ffplay (mobile)
    //nginx_rtmp (dvr)
    //service (dvr-cloud-proxy)
    public static final String HOST = "127.0.0.1";
    public static final int HOST_PORT = 80;
    public static final int LOCAL_PORT = 6666;
    public static final int LEN = 1024;

    public static void main(String[] args) throws IOException {
        Server server = new Server();
        ServerSocket serverSocket = new ServerSocket(LOCAL_PORT);
        server.start(serverSocket).join();
    }

    public static class Server {
        public CompletableFuture<Void> start(ServerSocket serverSocket) {
            return CompletableFuture.runAsync(() -> {
                try (serverSocket) {
                    while (!serverSocket.isClosed()) {
                        Socket clientSocket = serverSocket.accept();
                        CompletableFuture.runAsync(new RtmpProxySocket(clientSocket));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }
    }

    private static class RtmpProxySocket implements Runnable {
        private final Socket clientSocket;

        public RtmpProxySocket(Socket clientSocket) {
            this.clientSocket = clientSocket;
        }

        public void run() {
            try (Socket rtmpServer = new Socket(HOST, HOST_PORT);) {
                CompletableFuture<Void> outFuture = CompletableFuture.runAsync(() -> {
                    byte[] buffer = new byte[LEN];
                    try (InputStream in = clientSocket.getInputStream();
                         OutputStream outRtmpServer = rtmpServer.getOutputStream();
                    ) {
                        while (!clientSocket.isClosed() && !rtmpServer.isClosed()) {
                            int read = in.read(buffer);
                            outRtmpServer.write(buffer, 0, read);
                            System.out.println("Write to RTMP: " + new String(buffer, 0, read, StandardCharsets.UTF_8));
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
                CompletableFuture<Void> inFuture = CompletableFuture.runAsync(() -> {
                    byte[] buffer = new byte[LEN];
                    try (OutputStream out = clientSocket.getOutputStream();
                         InputStream inRtmpServer = rtmpServer.getInputStream();
                    ) {
                        while (!clientSocket.isClosed() && !rtmpServer.isClosed()) {
                            int read = inRtmpServer.read(buffer);
                            out.write(buffer, 0, read);
                            System.out.println("Write to Client: " + new String(buffer, 0, read, StandardCharsets.UTF_8));
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
                CompletableFuture.allOf(outFuture, inFuture).join();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}