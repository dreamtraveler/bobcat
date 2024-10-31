package com.bobcat.net;

import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.channels.SelectionKey;
import java.security.KeyStore;
import java.io.IOException;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManagerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLException;
import java.security.SecureRandom;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.io.EOFException;

public class SslSock implements ISock {
    static private SSLContext serverContext;
    static private SSLContext clientContext;
    private SSLEngine engine;
    private boolean isClient;
    private HandshakeStatus hsStatus;

    private ByteBuffer myAppData;
    private ByteBuffer peerAppData;
    private ByteBuffer peerNetData;
    private ByteBuffer myNetData;

    private static ExecutorService executor = Executors.newSingleThreadExecutor();
    private EOFException endException = new EOFException();

    SslSock(boolean isClient) {
        this.isClient = isClient;
    }

    @Override
    public boolean init() {
        try {
            SSLContext sslContext = isClient ? clientContext : serverContext;
            if (sslContext == null) {
                // System.setProperty("javax.net.debug", "ssl:handshake");
                String curlPath = System.getProperty("user.dir");
                String ksPath = curlPath + "/src/main/resources/mykeystore.jks";
                String trustPath = curlPath + "/src/main/resources/mykeystore.jks";
                char[] password = "123456".toCharArray();

                KeyStore ks = KeyStore.getInstance("JKS");
                KeyStore ksTrust = KeyStore.getInstance("JKS");
                try (FileInputStream ksFs = new FileInputStream(ksPath);
                        FileInputStream trustFs = new FileInputStream(trustPath)) {
                    ks.load(ksFs, password);
                    ks.load(trustFs, password);
                }
                KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                kmf.init(ks, password);
                TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                tmf.init(ksTrust);

                sslContext = SSLContext.getInstance("TLSv1.2");
                sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());
            }
            if (isClient) {
                engine = sslContext.createSSLEngine("127.0.0.1", 9027);
                engine.setUseClientMode(true);
                clientContext = sslContext;
            } else {
                engine = sslContext.createSSLEngine();
                engine.setUseClientMode(false);
                serverContext = sslContext;
            }

            SSLSession session = engine.getSession();
            int appBufferSize = session.getApplicationBufferSize();
            int packetBufferSize = session.getPacketBufferSize();
            myAppData = ByteBuffer.allocate(appBufferSize);
            peerAppData = ByteBuffer.allocate(appBufferSize);
            peerNetData = ByteBuffer.allocate(packetBufferSize);
            myNetData = ByteBuffer.allocate(packetBufferSize);

            engine.beginHandshake();

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    boolean isConnected() {
        return hsStatus == HandshakeStatus.FINISHED;
    }

    private ByteBuffer enlargeBuffer(ByteBuffer buffer, int sessionProposedCapacity) {
        if (sessionProposedCapacity > buffer.capacity()) {
            buffer = ByteBuffer.allocate(sessionProposedCapacity);
        } else {
            buffer = ByteBuffer.allocate(buffer.capacity() * 2);
        }
        return buffer;
    }

    private ByteBuffer handleBufferUnderflow(ByteBuffer buffer, int sessionProposedCapacity) {
        if (sessionProposedCapacity < buffer.limit()) {
            return buffer;
        } else {
            ByteBuffer replaceBuffer = enlargeBuffer(buffer, sessionProposedCapacity);
            buffer.flip();
            replaceBuffer.put(buffer);
            return replaceBuffer;
        }
    }

    public boolean handshake(SocketChannel channel, SelectionKey key) throws IOException {
        if (isConnected()) {
            return true;
        }
        if (key != null) {
            key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
        }
        System.err.println("About to do handshake...");

        SSLEngineResult result;
        SSLSession session = engine.getSession();
        hsStatus = engine.getHandshakeStatus();
        while (hsStatus != HandshakeStatus.FINISHED && hsStatus != HandshakeStatus.NOT_HANDSHAKING) {
            switch (hsStatus) {
                case NEED_UNWRAP:
                    if (peerNetData.limit() < peerNetData.capacity()) {
                        peerNetData.compact();
                    }
                    int ret = channel.read(peerNetData);
                    if (ret < 0) {
                        throw endException;
                    } else if (ret == 0) {
                        return false;
                    }
                    try {
                        peerNetData.flip();
                        result = engine.unwrap(peerNetData, peerAppData);
                        hsStatus = result.getHandshakeStatus();
                        System.out.printf("isConnected=%b, pos=%d, limit=%d\n", isConnected(), peerAppData.position(), peerNetData.limit());
                    } catch (SSLException sslException) {
                        System.out.println(
                                "A problem was encountered while processing the data that caused the SSLEngine to abort. Will try to properly close connection...");
                        throw sslException;
                    }
                    switch (result.getStatus()) {
                        case OK:
                            break;
                        case BUFFER_OVERFLOW:
                            peerAppData = enlargeBuffer(peerAppData, session.getApplicationBufferSize());
                            break;
                        case BUFFER_UNDERFLOW:
                            peerNetData = handleBufferUnderflow(peerNetData, engine.getSession().getPacketBufferSize());
                            break;
                        case CLOSED:
                            hsStatus = engine.getHandshakeStatus();
                            throw endException;
                        default:
                            throw new IllegalStateException("Invalid SSL status: " + result.getStatus());
                    }
                    break;
                case NEED_WRAP:
                    myNetData.clear();
                    myAppData.clear();
                    try {
                        result = engine.wrap(myAppData, myNetData);
                        hsStatus = result.getHandshakeStatus();
                    } catch (SSLException sslException) {
                        System.out.println(
                                "A problem was encountered while processing the data that caused the SSLEngine to abort. Will try to properly close connection...");
                        throw sslException;
                    }
                    switch (result.getStatus()) {
                        case OK:
                            myNetData.flip();
                            while (myNetData.hasRemaining()) {
                                channel.write(myNetData);
                            }
                            break;
                        case BUFFER_OVERFLOW:
                            myNetData = enlargeBuffer(myNetData, session.getPacketBufferSize());
                            break;
                        case BUFFER_UNDERFLOW:
                            throw new SSLException(
                                    "Buffer underflow occured after a wrap. I don't think we should ever get here.");
                        case CLOSED:
                            throw endException;
                        default:
                            throw new IllegalStateException("Invalid SSL status: " + result.getStatus());
                    }
                    break;
                case NEED_TASK:
                    if (key == null) {
                        return false;
                    }
                    Runnable task = engine.getDelegatedTask();
                    if (task != null) {
                        executor.execute(() -> {
                            task.run();
                            System.err.println("handshake About NEED_TASK...FINISH");
                            key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
                        });
                    }
                    System.err.println("handshake About NEED_TASK...");
                    hsStatus = engine.getHandshakeStatus();
                    return isConnected();
                case FINISHED:
                    break;
                case NOT_HANDSHAKING:
                    break;
                default:
                    throw new IllegalStateException("Invalid SSL status: " + hsStatus);
            }
        }
        System.out.println("hsStatus=" + hsStatus);
        return isConnected();
    }

    @Override
    public void reset() {
        myAppData.clear();
        peerAppData.clear();
        peerNetData.clear();
        myNetData.clear();
    }

    @Override
    public int read(SocketChannel channel, SelectionKey key, Stream stream) throws IOException {
        if (!handshake(channel, key)) {
            return 0;
        }

        System.out.println("About to read from a client...");
        if (peerNetData.limit() < peerNetData.capacity()) {
            peerNetData.compact();
        }
        int bytesRead = channel.read(peerNetData);
        peerNetData.flip();
        if (bytesRead > 0 || peerNetData.limit() > 0) {
            while (peerNetData.hasRemaining()) {
                stream.ensureWritable(peerNetData.limit());
                peerAppData = stream.unusedBuf();
                SSLEngineResult result = engine.unwrap(peerNetData, peerAppData);
                switch (result.getStatus()) {
                    case OK:
                        int readN = (peerAppData.position() - peerAppData.arrayOffset());
                        return readN;
                    case BUFFER_OVERFLOW:
                        stream.ensureWritable(engine.getSession().getApplicationBufferSize());
                        break;
                    case BUFFER_UNDERFLOW:
                        peerNetData = handleBufferUnderflow(peerNetData, engine.getSession().getPacketBufferSize());
                        return 0;
                    case CLOSED:
                        System.out.println("Client wants to close connection...");
                        return -1;
                    default:
                        throw new IllegalStateException("Invalid SSL status: " + result.getStatus());
                }
            }
        } else if (bytesRead < 0) {
            return bytesRead;
        }
        return 0;
    }

    @Override
    public int write(SocketChannel channel, SelectionKey key, Stream stream) throws IOException {
        if (!handshake(channel, key)) {
            return 0;
        }

        System.err.println("About to write to a client...");
        while (stream.len() > 0) {
            myAppData = stream.availableBuf();
            myNetData.clear();
            SSLEngineResult result = engine.wrap(myAppData, myNetData);
            switch (result.getStatus()) {
                case OK:
                    myNetData.flip();
                    while (myNetData.hasRemaining()) {
                        channel.write(myNetData);
                    }
                    System.out.printf("Message sent [%d] bytes to the client\n", stream.len());
                    return stream.len();
                case BUFFER_OVERFLOW:
                    myNetData = enlargeBuffer(myNetData, engine.getSession().getPacketBufferSize());
                    break;
                case BUFFER_UNDERFLOW:
                    throw new SSLException(
                            "Buffer underflow occured after a wrap. I don't think we should ever get here.");
                case CLOSED:
                    throw endException;
                default:
                    throw new IllegalStateException("Invalid SSL status: " + result.getStatus());
            }
        }
        return 0;
    }

    @Override
    public void close(SocketChannel channel) {
        if (engine == null) {
            return;
        }
        engine.closeOutbound();
        try {
            if (channel != null) {
                handshake(channel, null);
            }
            engine.closeInbound();
        } catch (Throwable e) {
            try {
                engine.closeInbound();
            } catch (Throwable ex) {
            }
        }
        engine.closeOutbound();
        engine = null;
        myAppData = null;
        peerAppData = null;
        peerNetData = null;
        myNetData = null;
        endException = null;
    }
}
