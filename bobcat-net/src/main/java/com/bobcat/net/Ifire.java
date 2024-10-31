package com.bobcat.net;

import java.nio.channels.SelectionKey;

public interface Ifire {
    default void onRead() {};

    default void onWrite() {}

    default void onAccept() {}

    default void onConnect() {}

    SelectionKey getKey();

    default void cancelRead() {
        getKey().interestOps(getKey().interestOps() & ~SelectionKey.OP_READ);
    }

    default void cancelWrite() {
        getKey().interestOps(getKey().interestOps() & ~SelectionKey.OP_WRITE);
    }

    default void addWrite() {
        getKey().interestOps(getKey().interestOps() | SelectionKey.OP_WRITE);
    }

    default void cancelConnect() {
        getKey().interestOps(getKey().interestOps() & ~SelectionKey.OP_CONNECT);
    }

    default void cancelAccept() {
        getKey().interestOps(getKey().interestOps() & ~SelectionKey.OP_ACCEPT);
    }
}