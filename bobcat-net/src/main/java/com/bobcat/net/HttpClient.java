package com.bobcat.net;

import com.bobcat.common.Reentrant;
import com.bobcat.net.http.FieldData;
import com.bobcat.net.http.HTTPCode;
import com.bobcat.net.http.HTTPContext;
import com.bobcat.net.http.HTTPParser;
import com.bobcat.net.http.ParserSettings;
import com.bobcat.net.http.ParserType;
import java.io.EOFException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

public class HttpClient implements Ifire {

    private long id = 0;
    private SocketChannel channel;
    private SelectionKey key;
    private HTTPParser parser;
    private ParserSettings settings;
    public HTTPContext context;
    private Stream writeStream = new Stream(1024);
    private boolean isKeepAlive = true;
    private boolean isChunked = false;
    private boolean isClose = false;
    private ISock sock;
    private Reentrant resumeReent;
    private boolean isClient = false;

    private EOFException endException = new EOFException();

    private static final byte[] schemaBytes = "HTTP/1.1".getBytes();
    private static final byte[] spaceBytes = " ".getBytes();
    private static final byte[] colonBytes = ": ".getBytes();
    private static final byte[] sepBytes = "\r\n".getBytes();
    private static final byte[] chunkEndBytes = "0\r\n\r\n".getBytes();

    HttpClient(long id) {
        this.id = id;
    }

    public void init(
            SocketChannel channel,
            boolean isClient,
            String host,
            int port) throws Exception {
        this.channel = channel;

        this.isClient = isClient;
        sock = new SslSock();
        // sock = new TcpSock(false);
        sock.init(isClient, host, port);
        int ops = SelectionKey.OP_READ;
        ParserType parserType = ParserType.HTTP_REQUEST;
        if (isClient) {
            ops |= SelectionKey.OP_WRITE;
            ops |= SelectionKey.OP_CONNECT;
            parserType = ParserType.HTTP_RESPONSE;
        }
        key = App.impl().register(channel, ops, this);
        initHTTPParser(parserType);
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

    public boolean isClose() {
        return isClose;
    }

    public void close() {
        if (isClose) {
            return;
        }
        System.out.printf("close channel id[%d]\n", id);
        isClose = true;
        if (sock != null) {
            sock.close(channel);
        }
        if (key != null) {
            key.cancel();
        }
        EventLoop.closeChannelSilently(channel);
        writeStream.destory();
        if (context != null) {
            context.destory();
        }
        sock = null;
        context = null;
        writeStream = null;
        channel = null;
        key = null;
        parser = null;
        settings = null;
        endException = null;
        HttpServer.impl().DelClient(id);
    }

    private void initHTTPParser(ParserType type) {
        parser = new HTTPParser(type);
        settings = new ParserSettings();
        context = new HTTPContext();

        settings.on_message_begin = p -> {
            return 0;
        };
        settings.on_url = (p, buf, pos, len) -> {
            FieldData url = context.url;
            if (url.off == 0) {
                url.off = pos;
            }
            if (url.off > 0) {
                url.len += len;
            }
            return 0;
        };
        settings.on_fragment = (p, buf, pos, len) -> {
            return 0;
        };
        settings.on_status_complete = p -> {
            return 0;
        };
        settings.on_header_field = (p, buf, pos, len) -> {
            if (context.state == HTTPContext.HPhase.VALUE) {
                context.state = HTTPContext.HPhase.FIELD;
                HTTPContext.Head h = context.addHead();
                h.name.off = pos;
            }
            HTTPContext.Head h = context.getLastHead();
            h.name.len += len;
            return 0;
        };
        settings.on_header_value = (p, buf, pos, len) -> {
            HTTPContext.Head h = context.getLastHead();
            if (context.state == HTTPContext.HPhase.FIELD) {
                context.state = HTTPContext.HPhase.VALUE;
                h.value.off = pos;
            }
            if (h.value.off > 0) {
                h.value.len += len;
            }
            return 0;
        };
        settings.on_headers_complete = p -> {
            return 0;
        };
        settings.on_body = (p, buf, pos, len) -> {
            FieldData body = context.body;
            if (body.off == 0) {
                body.off = pos;
            }
            if (body.off > 0) {
                body.len += len;
            }
            return 0;
        };
        settings.on_message_complete = p -> {
            System.out.println("on_message_complete");
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
        sock.reset();
    }

    public static HttpClient get(Reentrant reent, String url) {
        HttpClient client = HttpServer.impl().AddClient();
        try {
            client.makeRequest(reent, "GET", url, null);
            return client;
        } catch (Exception e) {
            client.close();
            e.printStackTrace();
            return null;
        }
    }

    public static HttpClient post(Reentrant reent, String url, byte[] body) {
        HttpClient client = HttpServer.impl().AddClient();
        try {
            client.makeRequest(reent, "POST", url, body);
            return client;
        } catch (Exception e) {
            client.close();
            e.printStackTrace();
            return null;
        }
    }

    public void makeRequest(Reentrant reent, String method, String url, byte[] body) throws Exception {
        resumeReent = reent;
        channel = SocketChannel.open();
        channel.configureBlocking(false);
        URI uri = URI.create(url);
        int port = uri.getPort();
        if (port == -1) {
            port = uri.getScheme().equals("https") ? 443 : 80;
        }
        init(channel, true, uri.getHost(), port);
        InetSocketAddress address = new InetSocketAddress(uri.getHost(), port);
        channel.connect(address);

        String query = uri.getRawQuery();
        String path = query == null ? uri.getRawPath() : uri.getRawPath() + "?" + query;
        AddMethodUrl(method, path);
        addHeadKV("Host", address.getHostName());
        addHeadKV("User-Agent", "bobcat");
        addHeadKV("Accept", "*/*");
        int len = body == null ? 0 : body.length;
        addHeadKV("Content-Length", String.valueOf(len));
        addHeadEnd();
        if (body != null) {
            writeStream.append(body);
        }
        System.err.println("makeRequest:\n" + StandardCharsets.UTF_8.decode(writeStream.availableBuf()));
    }

    @Override
    public void onRead() {
        System.err.println("onRead");
        if (!channel.isOpen()) {
            key.cancel();
            return;
        }
        try {
            while (true) {
                int readBytes = sock.read(channel, key, context.stream);
                if (readBytes > 0) {
                    int ret = parser.execute(settings, context.stream.peekBuf(readBytes));
                    context.stream.addSize(readBytes);
                    if (ret != readBytes) {
                        System.out.printf("http parse error ret[%d] != readBytes[%d]:%s\n", ret, readBytes,
                                StandardCharsets.UTF_8.decode(context.stream.availableBuf()).toString());
                        throw endException;
                    }
                    if (context.completed > 0) {
                        if (isClient && resumeReent != null) {
                            Reentrant reent = resumeReent;
                            resumeReent = null;
                            reent.run(null);
                            reset();
                        } else {
                            HttpServer.impl().serveHTTP(this);
                            reset();
                        }
                        break;
                    }
                } else if (readBytes < 0) {
                    System.out.println("peer close the socket");
                    throw endException;
                } else {
                    break;
                }
            }
        } catch (Exception e) {
            close();
        }
    }

    @Override
    public void onWrite() {
        System.err.println("onWrite");
        if (!channel.isOpen()) {
            key.cancel();
            return;
        }
        try {
            int n = sock.write(channel, key, writeStream);
            if (n > 0) {
                writeStream.skip(n);
                if (writeStream.len() > 0) {
                    addWrite();
                } else {
                    cancelWrite();
                }
            } else {
                cancelWrite();
            }
        } catch (Exception e) {
            e.printStackTrace();
            close();
        }
    }

    public void AddMethodUrl(String method, String url) {
        writeStream.append(method.getBytes());
        writeStream.append(spaceBytes);
        writeStream.append(url.getBytes());
        writeStream.append(spaceBytes);
        writeStream.append(schemaBytes);
        writeStream.append(sepBytes);
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

    public void reply(int code, ByteBuffer buf) {
        reply(code, buf, null);
    }

    public void reply(int code, ByteBuffer buf, IAddHeader addHeader) {
        // write head
        addHeadCode(code);
        if (addHeader != null) {
            addHeader.call(this);
        } else {
            addHeadKV("Content-Type", "text/plain; charset=utf-8");
        }
        if (isKeepAlive && code == 200) {
            addHeadKV("Connection", "keep-alive");
        } else {
            addHeadKV("Connection", "close");
        }
        int len = buf == null ? 0 : buf.remaining();
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
