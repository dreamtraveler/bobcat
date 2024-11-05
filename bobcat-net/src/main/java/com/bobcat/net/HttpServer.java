package com.bobcat.net;

import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.SelectionKey;
import java.util.Map;
import java.util.HashMap;
import java.lang.Long;

public class HttpServer implements Ifire {
    boolean stopped = false;
    SelectionKey listener;
    ServerSocketChannel listenerChannel;
    long genId = 1;
    String ip = "127.0.0.1";
    int port = 8080;

    private final Map<Long, HttpClient> clienMap = new HashMap<>();
    private Map<String, ICreateHttpHandler> handlerMap = new HashMap<>();

    private static HttpServer instance;

    private HttpServer() {}

    public static HttpServer impl() {
        if (instance == null) {
            instance = new HttpServer();
        }
        return instance;
    }

    public void start(EventLoop loop) {
        try {
            ip = "127.0.0.1";
            port = 8080;
            listenerChannel = ServerSocketChannel.open();
            listenerChannel.socket().bind(new InetSocketAddress(ip, port));
            listenerChannel.configureBlocking(false);
            listener = loop.register(listenerChannel, SelectionKey.OP_ACCEPT, this);
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

    public HttpClient AddClient() {
        genId++;
        HttpClient client = new HttpClient(genId);
        clienMap.put(genId, client);
        return client;
    }

    public HttpClient GetClient(long id) {
        try {
            return clienMap.get(id);
        } catch (Exception e) {
            return null;
        }
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
        HttpClient client = null;
        try {
            SocketChannel channel = listenerChannel.accept();
            channel.configureBlocking(false);
            client = AddClient();
            client.init(channel, false, ip, port);
            System.out.println("accept connection:" + channel.getRemoteAddress().toString());
        } catch (Throwable e) {
            e.printStackTrace();
            if (client != null) {
                client.close();
            }
        }
    }

    public void register(String path, ICreateHttpHandler handler) {
        handlerMap.put(path, handler);
    }

    public void serveHTTP(HttpClient client) {
        String bufStr = client.context.stream.bufToString();
        System.out.println("ServeHTTP:\n" + bufStr);

        String path = client.context.getPath();
        ICreateHttpHandler creater = handlerMap.get(path);
        if (creater == null) {
            client.reply(404);
            return;
        }

        try {
            HttpReentrant reent = creater.create();
            reent.handle(client);
            reent.run(null);
        } catch (Exception e) {
            e.printStackTrace();
            client.reply(500);
        }
    }
}
