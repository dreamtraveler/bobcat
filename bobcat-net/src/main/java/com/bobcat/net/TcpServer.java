package com.bobcat.net;

import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.SelectionKey;
import java.util.Map;
import java.util.HashMap;
import java.lang.Long;
import java.io.IOException;

public class TcpServer implements Ifire {
    boolean stopped = false;
    SelectionKey listener;
    ServerSocketChannel listenerChannel;
    long genId = 1;
    EventLoop event_loop;

    private final Map<Long, TcpClient> clienMap = new HashMap<>();

    private static TcpServer instance;

    private TcpServer() {}

    public static TcpServer impl() {
        if (instance == null) {
            instance = new TcpServer();
        }
        return instance;
    }

    public void start(EventLoop loop) {
        this.event_loop = loop;
        try {
            String ip = "127.0.0.1";
            int port = 8080;
            listenerChannel = ServerSocketChannel.open();
            listenerChannel.socket().bind(new InetSocketAddress(ip, port));
            listenerChannel.configureBlocking(false);
            listener = event_loop.register(listenerChannel, SelectionKey.OP_ACCEPT, this);
            System.err.printf("server listen on %s:%d\n", ip, port);
        } catch (Exception e) {
            System.err.println("Failed to start protocol server accepter");
        }
    }

    public void stop() {
        if (!listenerChannel.isOpen())
            return;
        try {
            listenerChannel.close();
            stopped = true;
        } catch (Throwable e) {
        }
    }

    @Override
    public SelectionKey getKey() {
        return listener;
    }

    public void AddClient(SocketChannel channel) throws IOException {
        genId++;
        TcpClient client = new TcpClient(genId, channel, event_loop);
        clienMap.put(genId, client);
    }

    public void DelClient(long id) {
        clienMap.remove(id);
        if (clienMap.size() == 0) {
            System.out.println("call System.gc()");
            System.gc();
        }
    }

    @Override
    public void onAccept() {
        SocketChannel channel = null;
        try {
            channel = listenerChannel.accept();
            channel.configureBlocking(false);
            AddClient(channel);
            System.out.println("accept connection:" + channel.getRemoteAddress().toString());
        } catch (Throwable e) {
            System.out.println("failed to accept connection:" + e.toString());
            System.exit(0);
        }
    }
}
