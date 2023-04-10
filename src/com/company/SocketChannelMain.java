package com.company;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CompletableFuture;

public class SocketChannelMain {

    //ffplay -> service (TCP) -> nginx_rtmp
    //ffplay -> service (TCP) <- dvr || establish connection
    //ffplay <-> service (rtmp) <-> dvr || traffic
    //ffplay (mobile)
    //nginx_rtmp (dvr)
    //service (dvr-cloud-proxy)
    public static final String HOST = "127.0.0.1";
    public static final int HOST_PORT = 80;
    public static final int LOCAL_PORT = 6666;
    public static final int LEN = 32;

    public static void main(String[] args) {
        Server server = new Server();
        try (
                ServerSocketChannel serverSocketChannel = ServerSocketChannel.open()
        ) {
            serverSocketChannel.getLocalAddress();
            serverSocketChannel.bind(new InetSocketAddress(LOCAL_PORT));
            server.start(serverSocketChannel.socket()).join();
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
                        RtmpProxyChannel rtmpProxyChannel = new RtmpProxyChannel(clientSocket);
                        new Thread(rtmpProxyChannel).start();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
        }
    }

    private static class RtmpProxyChannel implements Runnable {
        private final Socket clientSocket;

        public RtmpProxyChannel(Socket clientSocket) {
            this.clientSocket = clientSocket;
        }

        public void run() {
            try (SocketChannel clientSocketChannel = clientSocket.getChannel();
                 SocketChannel rtmpSocketChannel = SocketChannel.open(new InetSocketAddress(HOST, HOST_PORT));
                 Socket rtmpServer = rtmpSocketChannel.socket();
            ) {
                CompletableFuture<Void> outFuture = CompletableFuture.runAsync(() -> {
                    ByteBuffer buffer = ByteBuffer.allocate(LEN);
                    while (clientSocket.isConnected() && rtmpServer.isConnected()) {
                        try {
                            clientSocketChannel.read(buffer);
                            rtmpSocketChannel.write(buffer);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                });
                CompletableFuture<Void> inFuture = CompletableFuture.runAsync(() -> {
                    ByteBuffer buffer = ByteBuffer.allocate(LEN);
                    while (clientSocket.isConnected() && rtmpServer.isConnected()) {
                        try {
                            rtmpSocketChannel.read(buffer);
                            clientSocketChannel.write(buffer);
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