package com.bobcat.common;

final public class RpcRank extends Reentrant {
    RpcRank() {
        ablock(ab -> {
            System.out.println("RpcRank first normal calcu");
        });
        ablock(ab -> {
            System.out.println("RpcRank second normal calcu");
        }).onRequest(ab -> {
            System.out.println("RpcRank send second remote msg");
        }).onError(ab -> {
            System.out.println("RpcRank second msg error");
        }).onSuccess(ab -> {
            Message rsp = ab.getResponseMsg();
            System.out.printf("RpcRank second msg success:%s\n", rsp.data);
        });
    }
}
