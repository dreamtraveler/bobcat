package com.bobcat.common;

public class Main {
    public static void main(String[] args) {
        Reentrant rpc1 = new RpcLogin();
        Reentrant rpc2 = new RpcRank();
        Reentrant rpc3 = new RpcTail();
        rpc1.setNext(rpc2).setNext(rpc3);
        for (int i = 0; i < 5; i++) {
            System.out.printf("###%d begin enter\n", i);
            if (i == 3) {
                Message msg = new Message(0);
                msg.data = "say tanlei";
                rpc1.run(msg);
            } else {
                rpc1.run(null);
            }
        }
    }
}