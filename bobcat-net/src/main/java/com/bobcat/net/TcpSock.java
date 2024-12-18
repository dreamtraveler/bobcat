package com.bobcat.net;

import java.nio.channels.SocketChannel;
import java.nio.channels.SelectionKey;
import java.io.IOException;

public class TcpSock implements ISock {

    @Override
    public void init(boolean isClient, String host, int port) throws Exception {}

    @Override
    public int read(SocketChannel channel, SelectionKey key, Stream stream) throws IOException {
        stream.ensureWritable(1024);
        return channel.read(stream.unusedBuf());
    }

    @Override
    public int write(SocketChannel channel, SelectionKey key, Stream stream) throws IOException {
        int n = channel.write(stream.availableBuf());
        return n;
    }

    @Override
    public void close(SocketChannel channel) {}

    @Override
    public void reset() {}
}
