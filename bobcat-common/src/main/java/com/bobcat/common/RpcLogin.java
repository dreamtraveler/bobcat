package com.bobcat.common;

final public class RpcLogin extends Reentrant {
    private int retry_count = 0;
    private int rewait_count = 0;

    RpcLogin() {
        ablock(ab -> {
            System.out.println("normal calculate");
        }).onRequest(ab -> {
            System.out.println("send first remote msg");
        }).onError(ab -> {
            System.out.println("first msg error");
        }).onSuccess(ab -> {
            ab.getResponseMsg();
            System.out.println("first msg success");
            // if (++retry_count < 3) {
            // ab.retry(this, 300);
            // }
            // if (++rewait_count < 4) {
            // ab.rewait();
            // }
        }).onFinal(ab -> {
            System.out.println("first msg msg final");
        });
        ablock(ab -> {
            System.out.println("second normal calcu");
        }).onRequest(ab -> {
            System.out.println("send second remote msg");
        }).onError(ab -> {
            System.out.println("second msg onError");
        }).onSuccess(ab -> {
            System.out.println("second msg onSuccess");
        });
    }
}
