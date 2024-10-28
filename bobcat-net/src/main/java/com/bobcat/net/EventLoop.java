package com.bobcat.net;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;
import java.nio.channels.SelectableChannel;
import java.net.Socket;

public class EventLoop {
    private Selector selector;

    public int init() throws IOException {
        selector = Selector.open();
        return 0;
    }

    public void select(long timeout) throws IOException {
        selector.select(timeout);
    }

    public SelectionKey register(SelectableChannel channel, int ops, Ifire att) throws IOException {
        return channel.register(selector, ops, att);
    }

    public void handleSelectedKeys() {
        Set<SelectionKey> keys = selector.selectedKeys();
        Iterator<SelectionKey> iterator = keys.iterator();
        while (iterator.hasNext()) {
            SelectionKey key = iterator.next();
            Ifire att = (Ifire) key.attachment();
            iterator.remove();
            if (key.isValid()) {
                int readyOps = key.readyOps();
                if ((readyOps & SelectionKey.OP_READ) != 0) {
                    att.onRead();
                } else if ((readyOps & SelectionKey.OP_WRITE) != 0) {
                    att.onWrite();
                } else if ((readyOps & SelectionKey.OP_ACCEPT) != 0) {
                    att.onAccept();
                } else if ((readyOps & SelectionKey.OP_CONNECT) != 0) {
                    att.onConnect();
                } else {
                    key.cancel();
                }
            } else {
                key.cancel();
            }
        }
    }

    static void closeChannelSilently(SocketChannel channel) {
        if (channel != null) {
            Socket socket = channel.socket();
            if (socket != null) {
                try {
                    socket.close();
                } catch (Throwable e) {
                }
            }
            try {
                channel.close();
            } catch (Throwable e) {
            }
        }
    }
}
