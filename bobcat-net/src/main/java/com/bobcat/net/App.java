package com.bobcat.net;

import java.io.IOException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;

public class App {
    boolean running = false;
    private EventLoop loop = new EventLoop();

    private static App instance;

    private App() {};

    static App impl() {
        if (instance == null) {
            instance = new App();
        }
        return instance;
    }

    boolean init() {
        HttpServer server = HttpServer.impl();
        server.register("/hello", () -> new HelloHandler());

        try {
            loop.init();
            server.start(loop);
            return true;
        } catch (Exception e) {
            System.out.println(e.toString());
            return false;
        }
    }

    public SelectionKey register(SelectableChannel channel, int ops, Ifire att) throws IOException {
        return loop.register(channel, ops, att);
    }

    public void run(String[] args) {
        if (!init()) {
            return;
        }
        running = true;
        while (running) {
            try {
                loop.select(10);
                loop.handleSelectedKeys();
            } catch (Exception e) {
                System.out.println(e.toString());
                return;
            }
        }
    }
}
