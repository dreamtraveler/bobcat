package com.bobcat.net;

import java.io.EOFException;
import java.nio.channels.SocketChannel;
import java.nio.channels.SelectionKey;
import java.io.IOException;
import java.nio.ByteBuffer;

import com.bobcat.net.http.FieldData;
import com.bobcat.net.http.HTTPContext;
import com.bobcat.net.http.HTTPParser;
import com.bobcat.net.http.HTTPCode;
import com.bobcat.net.http.ParserSettings;
import com.bobcat.net.http.ParserType;

public class HttpClient implements Ifire {
    private long id = 0;
    private SocketChannel channel;
    private SelectionKey key;
    HTTPParser parser;
    ParserSettings settings;
    public HTTPContext context;
    Stream writeStream = new Stream(1024);
    private boolean isKeepAlive = true;
    private boolean isChunked = false;
    private boolean isClose = false;

    private EOFException endException = new EOFException();

    private static final byte[] schemaBytes = "HTTP/1.1".getBytes();
    private static final byte[] spaceBytes = " ".getBytes();
    private static final byte[] colonBytes = ": ".getBytes();
    private static final byte[] sepBytes = "\r\n".getBytes();
    private static final byte[] chunkEndBytes = "0\r\n\r\n".getBytes();

    HttpClient(long id, SocketChannel channel, EventLoop loop) throws IOException {
        this.id = id;
        this.channel = channel;
        key = loop.register(channel, SelectionKey.OP_READ, this);
        initHTTPParser();
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
        writeStream.destory();
        context.destory();
        context = null;
        writeStream = null;
        channel = null;
        key = null;
        parser = null;
        settings = null;
        endException = null;
        HttpServer.impl().DelClient(id);
    }

    private void initHTTPParser() {
        parser = new HTTPParser(ParserType.HTTP_REQUEST);
        settings = new ParserSettings();
        context = new HTTPContext();

        settings.on_message_begin = (p) -> {
            return 0;
        };
        settings.on_url = (p, buf, pos, len) -> {
            FieldData url = context.url;
            if (url.off == 0) {
                url.off = context.stream.size() + pos;
            }
            if (url.off > 0) {
                url.len += len;
            }
            return 0;
        };
        settings.on_fragment = (p, buf, pos, len) -> {
            return 0;
        };
        settings.on_status_complete = (p) -> {
            return 0;
        };
        settings.on_header_field = (p, buf, pos, len) -> {
            if (context.state == HTTPContext.HPhase.VALUE) {
                context.state = HTTPContext.HPhase.FIELD;
                HTTPContext.Head h = context.addHead();
                h.name.off = context.stream.size() + pos;
            }
            HTTPContext.Head h = context.getLastHead();
            h.name.len += len;
            return 0;
        };
        settings.on_header_value = (p, buf, pos, len) -> {
            HTTPContext.Head h = context.getLastHead();
            if (context.state == HTTPContext.HPhase.FIELD) {
                context.state = HTTPContext.HPhase.VALUE;
                h.value.off = context.stream.size() + pos;
            }
            if (h.value.off > 0) {
                h.value.len += len;
            }
            return 0;
        };
        settings.on_headers_complete = (p) -> {
            return 0;
        };
        settings.on_body = (p, buf, pos, len) -> {
            FieldData body = context.body;
            if (body.off == 0) {
                body.off = context.stream.size() + pos;
            }
            if (body.off > 0) {
                body.len += len;
            }
            return 0;
        };
        settings.on_message_complete = (p) -> {
            context.completed += 1;
            return 0;
        };
        settings.on_error = (HTTPParser parser, String mes, ByteBuffer buf, int initial_position) -> {
            System.out.printf("http parse error buf_position[%d]:%s\n", initial_position, mes);
        };
    }

    void reset() {
        parser = new HTTPParser(ParserType.HTTP_REQUEST);
        context.reset();
    }

    @Override
    public void onRead() {
        if (!channel.isOpen()) {
            key.cancel();
            return;
        }
        try {
            context.stream.ensureWritable(1024);
            ByteBuffer buffer = context.stream.unusedBuf();
            int readBytes = (int) channel.read(buffer);
            if (readBytes > 0) {
                int ret = parser.execute(settings, context.stream.peekBuf(readBytes));
                if (ret == readBytes) {
                    context.stream.addSize(readBytes);
                } else {
                    System.out.printf("http parse error ret[%d] != readBytes[%d]\n", ret, readBytes);
                    throw endException;
                }
                if (context.completed > 0) {
                    if (context.isClient) {
                        // Resume(0);
                    } else {
                        HttpServer.impl().serveHTTP(this);
                        reset();
                    }
                }

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
            channel.write(writeStream.availableBuf());
            cancelWrite();
        } catch (Exception e) {
            e.printStackTrace();
            close();
        }
    }

    public void addHeadCode(int code) {
        writeStream.append(schemaBytes);
        writeStream.append(spaceBytes);
        writeStream.append(String.valueOf(code).getBytes());
        writeStream.append(spaceBytes);
        String codeDesc = HTTPCode.getCodeStr(code);
        writeStream.append(codeDesc.getBytes());
        writeStream.append(sepBytes);
    }

    public void addHeadKV(String k, String v) {
        writeStream.append(k.getBytes());
        writeStream.append(colonBytes);
        writeStream.append(v.getBytes());
        writeStream.append(sepBytes);
    }

    public void addHeadEnd() {
        writeStream.append(sepBytes);
    }

    public void reply(int code) {
        reply(code, null, null);
    }

    public void reply(int code, byte[] buf) {
        reply(code, buf, null);
    }

    public void reply(int code, byte[] buf, IAddHeader addHeader) {
        // write head
        addHeadCode(code);
        if (addHeader != null) {
            addHeader.call(this);
        } else {
            addHeadKV("Content-Type", "text/plain; charset=utf-8");
        }
        if (isKeepAlive) {
            addHeadKV("Connection", "keep-alive");
        } else {
            addHeadKV("Connection", "close");
        }
        int len = buf == null ? 0 : buf.length;
        if (isChunked) {
            addHeadKV("Transfer-Encoding", "chunked");
        } else {
            addHeadKV("Content-Length", String.valueOf(len));
        }
        addHeadEnd();
        // write body
        if (buf != null) {
            if (isChunked) {
                byte[] chunkedLenBytes = String.format("%x\r\n", len).getBytes();
                writeStream.append(chunkedLenBytes);
                writeStream.append(buf);
                writeStream.append(sepBytes);
            } else {
                writeStream.append(buf);
            }
        }
        if (isChunked) {
            writeStream.append(chunkEndBytes);
        }
        System.out.println("Reply:\n" + writeStream.bufToString());
        onWrite();
    }
}
