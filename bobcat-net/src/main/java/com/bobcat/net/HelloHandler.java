package com.bobcat.net;

import java.nio.ByteBuffer;

public class HelloHandler extends HttpReentrant {
    HttpClient sub_client;
    long parentId;

    public void handle(HttpClient client) {
        ablock(ab -> {
            parentId = client.id();
        }).onRequest(ab -> {
            sub_client = HttpClient.get(this, "https://www.baidu.com/");
        }).onSuccess(ab -> {
            if (sub_client == null) {
                client.reply(500);
                return;
            }
            if (HttpServer.impl().GetClient(parentId) == null) {
                System.out.println("parent_client already release");
                client.reply(500);
                sub_client.close();
                return;
            }
            sub_client.context.iterator((key, value) -> {
                System.out.println("sub_client header:" + key + ":" + value);
                return 0;
            });
            ByteBuffer body = sub_client.context.getBodyBuf();
            client.reply(200, body, null);
            sub_client.close();
        });
    }
}
