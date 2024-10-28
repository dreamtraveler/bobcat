package com.bobcat.net;

import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.SelectionKey;
import java.util.Map;
import java.util.HashMap;
import java.lang.Long;
import java.io.IOException;

public class HttpServer implements Ifire {
    boolean stopped = false;
    SelectionKey listener;
    ServerSocketChannel listenerChannel;
    long genId = 1;
    EventLoop event_loop;

    private final Map<Long, HttpClient> clienMap = new HashMap<>();
    private Map<String, HttpHandler> handlerMap = new HashMap<>();

    private static HttpServer instance;

    private HttpServer() {}

    public static HttpServer impl() {
        if (instance == null) {
            instance = new HttpServer();
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
        HttpClient client = new HttpClient(genId, channel, event_loop);
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

    public void register(String path, HttpHandler handler) {
        handlerMap.put(path, handler);
    }

    public void serveHTTP(HttpClient client) {
        String bufStr = client.context.stream.bufToString();
        System.out.println("ServeHTTP:\n" + bufStr);

        String path = client.context.getPath();
        HttpHandler handler = handlerMap.get(path);
        if (handler != null) {
            try {
                int ret = handler.run(client);
                if (ret != 0) {
                    client.reply(ret);
                }
            } catch (Exception e) {
                e.printStackTrace();
                client.reply(500);
            }
        } else {
            client.reply(404);
        }
    }
}
