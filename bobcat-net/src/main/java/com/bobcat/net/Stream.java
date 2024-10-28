package com.bobcat.net;

import java.lang.RuntimeException;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;

public class Stream {

    final static int PAGE_SIZE = 4096;
    final static int STREAM_MAX_SIZE = 16 * 1024 * 1024;
    private int rpos = 0;
    private int size = 0;
    private int capacity = 0;
    private byte[] buf;

    public Stream(int cap) {
        if (cap > STREAM_MAX_SIZE) {
            cap = STREAM_MAX_SIZE;
        } else if (cap < 64) {
            cap = 64;
        }
        this.capacity = cap;
        buf = new byte[this.capacity];
    }

    public void destory() {
        buf = null;
        rpos = 0;
        size = 0;
        capacity = 0;
    }

    public int append(byte[] b) {
        ensureWritable(b.length);
        System.arraycopy(b, 0, buf, size, b.length);
        size = size + b.length;
        return 0;
    }

    public int roundup(int x, int y) {
        return ((x + y - 1) / y) * y;
    }

    public int ensureWritable(int dataLen) {
        if (dataLen <= 0) {
            throw new RuntimeException("error data_len=" + dataLen);
        }
        Shrink();

        int expectSize = size + dataLen;
        if (expectSize > STREAM_MAX_SIZE) {
            throw new RuntimeException("expectSize[" + expectSize + "]" + STREAM_MAX_SIZE);
        }
        if (capacity < expectSize) {
            expectSize = roundup(expectSize, PAGE_SIZE);
            capacity = capacity << 1;
            if (capacity < expectSize) {
                capacity = expectSize;
            }
        }
        byte[] newBuf = new byte[capacity];
        if (size > 0) {
            System.arraycopy(buf, 0, newBuf, 0, size);
        }
        buf = newBuf;
        return 0;
    }

    public void skip(int offset) {
        if (offset < 0) {
            return;
        }
        if (rpos + offset < size) {
            rpos += offset;
        } else {
            rpos = 0;
            size = 0;
        }
    }

    public void reset() {
        rpos = 0;
        size = 0;
    }

    public void Shrink() {
        if (rpos > 0) {
            int readableLen = size - rpos;
            System.arraycopy(buf, rpos, buf, 0, readableLen);
            rpos = 0;
            size = readableLen;
        }
    }

    public void addSize(int n) {
        if (size + n <= capacity) {
            size += n;
        }
    }

    public ByteBuffer unusedBuf() {
        return ByteBuffer.wrap(buf, size, capacity - size);
    }

    public int len() {
        return size - rpos;
    }

    public int size() {
        return size;
    }

    public ByteBuffer availableBuf() {
        return ByteBuffer.wrap(buf, rpos, len());
    }

    public ByteBuffer peekBuf(int count) {
        return ByteBuffer.wrap(buf, size, count);
    }

    public ByteBuffer slice(int offset, int len) {
        return ByteBuffer.wrap(buf, offset, len);
    }

    @Override
    public String toString() {
        return String.format("stream rpos=%d, size=%d, capacity=%d\n", rpos, size, capacity);
    }

    public String bufToString() {
        return StandardCharsets.UTF_8.decode(ByteBuffer.wrap(buf, rpos, size)).toString();
    }
}
