package com.bobcat.common;

final public class RpcTail extends Reentrant {
    RpcTail() {
        setTail();
    }

    public void run(Message msg) {
        System.out.println("send to client ret:" + msg.ret);
    }
}
