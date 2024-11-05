package com.bobcat.net;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Pipe;
import java.nio.channels.SelectionKey;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.Callable;

public class PipeHandler implements Ifire {

    private static ConcurrentLinkedQueue<Future> queue = new ConcurrentLinkedQueue<>();
    private final ReentrantLock writeLock = new ReentrantLock();
    private static ExecutorService executor = Executors.newCachedThreadPool();
    private Pipe.SourceChannel readChannel;
    private Pipe.SinkChannel writeChannel;
    private SelectionKey key;

    private final ByteBuffer oneByte = ByteBuffer.allocate(1);
    private ByteBuffer readBuffer = ByteBuffer.allocate(8);

    private static PipeHandler instance;

    private PipeHandler() {}

    public static PipeHandler impl() {
        if (instance == null) {
            instance = new PipeHandler();
            instance.init();
        }
        return instance;
    }

    void init() {
        try {
            Pipe pipe = Pipe.open();
            readChannel = pipe.source();
            writeChannel = pipe.sink();

            readChannel.configureBlocking(false);
            key = App.impl().register(readChannel, SelectionKey.OP_READ, this);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public SelectionKey getKey() {
        return key;
    }

    public static <T> AsyncCallback<T> Go(Callable<T> supply) {
        return impl().Go0(supply);
    }

    public <T> AsyncCallback<T> Go0(Callable<T> supply) {
        AsyncCallback<T> ac = new AsyncCallback<>(supply);
        executor.execute(() -> {
            ac.applySupply();
            queue.add(ac);
            syncNotify();
        });
        return ac;
    }

    public void onRead() {
        try {
            while (readChannel.read(readBuffer) > 0) {
                readBuffer.rewind();
            }
            Future future;
            while ((future = queue.poll()) != null) {
                future.applyCallback();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void syncNotify() {
        writeLock.lock();
        try {
            writeChannel.write(oneByte);
            oneByte.rewind();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            writeLock.unlock();
        }
    }
}
