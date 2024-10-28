package com.bobcat.net;

import java.io.EOFException;
import java.nio.channels.SocketChannel;
import java.nio.channels.SelectionKey;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class TcpClient implements Ifire {
    private long id = 0;
    private SocketChannel channel;
    private SelectionKey key;
    private boolean isClose = false;

    private final EOFException endException = new EOFException();

    TcpClient(long id, SocketChannel channel, EventLoop loop) throws IOException {
        this.id = id;
        this.channel = channel;
        key = loop.register(channel, SelectionKey.OP_READ, this);
    }

    long id() {
        return id;
    }

    @Override
    public SelectionKey getKey() {
        return key;
    }

    @Override
    public void onConnect() {
        try {
            cancelConnect();
            channel.finishConnect();
        } catch (Exception e) {
        }
    }

    public void close() {
        if (isClose) {
            return;
        }
        System.out.printf("close channel id[%d]\n", id);
        isClose = true;
        key.cancel();
        EventLoop.closeChannelSilently(channel);
        channel = null;
        key = null;
        TcpServer.impl().DelClient(id);
    }

    @Override
    public void onRead() {
        if (!channel.isOpen()) {
            key.cancel();
            return;
        }
        try {
            ByteBuffer buffer = ByteBuffer.allocate(24);
            int readBytes = (int) channel.read(buffer);
            if (readBytes > 0) {
                String s = StandardCharsets.UTF_8.decode(ByteBuffer.wrap(buffer.array(), 0, readBytes)).toString();
                System.out.printf("total read bytes[%d]:%s\n", readBytes, s);
                channel.write(ByteBuffer.wrap("hello a\n".getBytes()));
            } else if (readBytes < 0) {
                System.out.println("peer close the socket");
                throw endException;
            }
        } catch (Exception e) {
            close();
        }
    }

    @Override
    public void onWrite() {
        try {
            cancelWrite();
        } catch (Exception e) {
            close();
        }
    }
}
