package com.company;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
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

    public static void main(String[] args) {
        Server server = new Server();
        try (
                ServerSocket serverSocket = new ServerSocket(LOCAL_PORT);
        ) {
            server.start(serverSocket).join();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static class Server {
        public CompletableFuture<Void> start(ServerSocket serverSocket) {
            return CompletableFuture.runAsync(() -> {
                while (true) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        RtmpProxySocket rtmpProxyChannel = new RtmpProxySocket(clientSocket);
                        new Thread(rtmpProxyChannel).start();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
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
            try (OutputStream out = clientSocket.getOutputStream();
                 InputStream in = clientSocket.getInputStream();
                 Socket rtmpServer = new Socket(HOST, HOST_PORT);
                 OutputStream outRtmpServer = rtmpServer.getOutputStream();
                 InputStream inRtmpServer = rtmpServer.getInputStream();
            ) {
                CompletableFuture<Void> outFuture = CompletableFuture.runAsync(() -> {
                    while (clientSocket.isConnected() && rtmpServer.isConnected()) {
                        try {
                            outRtmpServer.write(in.read());
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                });
                CompletableFuture<Void> inFuture = CompletableFuture.runAsync(() -> {
                    while (clientSocket.isConnected() && rtmpServer.isConnected()) {
                        try {
                            out.write(inRtmpServer.read());
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                });
                CompletableFuture.allOf(outFuture, inFuture).join();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}