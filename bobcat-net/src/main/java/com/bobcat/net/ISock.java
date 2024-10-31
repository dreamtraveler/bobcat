package com.bobcat.net;

import java.nio.channels.SocketChannel;
import java.nio.channels.SelectionKey;
import java.io.IOException;

public interface ISock {
    boolean init();

    int read(SocketChannel channel, SelectionKey key, Stream stream) throws IOException;

    int write(SocketChannel channel, SelectionKey key, Stream stream) throws IOException;

    public void close(SocketChannel channel);

    public void reset();
}
