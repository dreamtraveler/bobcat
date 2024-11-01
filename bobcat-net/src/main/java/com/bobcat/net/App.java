package com.bobcat.net;

public class App {
    boolean running = false;

    public void run(String[] args) {
        EventLoop loop = new EventLoop();
        HttpServer server = HttpServer.impl();
        server.register("/hello", new HelloHandler());

        try {
            loop.init();
            server.start(loop);
        } catch (Exception e) {
            System.out.println(e.toString());
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
