package com.bobcat.net;

public class HelloHandler implements HttpHandler {
    public int run(HttpClient client) {
        client.reply(200, "hello bobcat.\n".getBytes());
        return 0;
    }
}
