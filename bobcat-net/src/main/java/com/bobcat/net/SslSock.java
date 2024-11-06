package com.bobcat.net;

import java.io.EOFException;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

public class SslSock implements ISock {

    private static SSLContext serverContext;
    private static SSLContext clientContext;
    private SSLEngine engine;
    private HandshakeStatus hsStatus;

    private ByteBuffer myAppData;
    private ByteBuffer peerAppData;
    private ByteBuffer peerNetData;
    private ByteBuffer myNetData;

    private static class TrustAllManager implements X509TrustManager {

        @Override
        public void checkClientTrusted(X509Certificate[] x509Certificates, String s) {}

        @Override
        public void checkServerTrusted(X509Certificate[] x509Certificates, String s) {}

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }

    @Override
    public void init(boolean isClient, String host, int port) throws Exception {
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
                if (isClient) {
                    sslContext.init(null, new TrustManager[] { new TrustAllManager() }, new SecureRandom());
                } else {
                    sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());
                }
            }
            if (isClient) {
                engine = sslContext.createSSLEngine(host, port);
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
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
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
        if (sessionProposedCapacity <= buffer.limit() || buffer.position() < buffer.limit()) {
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
        System.err.println("About to do handshake...");

        SSLEngineResult result;
        SSLSession session = engine.getSession();
        hsStatus = engine.getHandshakeStatus();
        while (hsStatus != HandshakeStatus.FINISHED && hsStatus != HandshakeStatus.NOT_HANDSHAKING) {
            System.out.println("while hsStatus=" + hsStatus);
            switch (hsStatus) {
                case NEED_UNWRAP:
                    int mark = peerNetData.position();
                    int ret = channel.read(peerNetData);
                    if (ret < 0) {
                        throw new EOFException();
                    }
                    try {
                        peerNetData.flip();
                        result = engine.unwrap(peerNetData, peerAppData);
                        peerNetData.compact();
                        hsStatus = result.getHandshakeStatus();
                    } catch (SSLException sslException) {
                        System.out.println(sslException.toString());
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
                            throw new EOFException();
                        default:
                            throw new IllegalStateException("Invalid SSL status: " + result.getStatus());
                    }
                    if (ret == 0 && hsStatus == HandshakeStatus.NEED_UNWRAP && peerNetData.position() == mark) {
                        return false;
                    } else {
                        break;
                    }
                case NEED_WRAP:
                    myNetData.clear();
                    myAppData.clear();
                    try {
                        result = engine.wrap(myAppData, myNetData);
                        hsStatus = result.getHandshakeStatus();
                    } catch (SSLException sslException) {
                        System.out.println(sslException.toString());
                        throw sslException;
                    }
                    switch (result.getStatus()) {
                        case OK:
                            myNetData.flip();
                            while (myNetData.hasRemaining()) {
                                channel.write(myNetData);
                            }
                            if (key == null) {
                                return false;
                            } else {
                                break;
                            }
                        case BUFFER_OVERFLOW:
                            myNetData = enlargeBuffer(myNetData, session.getPacketBufferSize());
                            break;
                        case BUFFER_UNDERFLOW:
                            throw new SSLException(
                                    "Buffer underflow occured after a wrap. I don't think we should ever get here.");
                        case CLOSED:
                            throw new EOFException();
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
                        PipeHandler.Go(() -> {
                            task.run();
                            if (key.isValid()) {
                                key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
                            }
                            return 0;
                        }).onComplete(ar -> {
                            if (key.isValid()) {
                                key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
                            }
                        });
                    }
                    hsStatus = engine.getHandshakeStatus();
                    return false;
                case FINISHED:
                    break;
                case NOT_HANDSHAKING:
                    break;
                default:
                    throw new IllegalStateException("Invalid SSL status: " + hsStatus);
            }
        }
        if (isConnected() && key != null && key.isValid()) {
            key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
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
        int bytesRead = channel.read(peerNetData);
        System.out.println("read bytesRead=" + bytesRead);
        if (bytesRead > 0 || peerNetData.position() > 0) {
            while (peerNetData.position() > 0) {
                stream.ensureWritable(peerNetData.limit());
                peerAppData = stream.unusedBuf();
                int mark = peerAppData.position();
                peerNetData.flip();
                SSLEngineResult result = engine.unwrap(peerNetData, peerAppData);
                peerNetData.compact();
                switch (result.getStatus()) {
                    case OK:
                        int readN = peerAppData.position() - mark;
                        peerAppData.flip();
                        return readN;
                    case BUFFER_OVERFLOW:
                        stream.ensureWritable(engine.getSession().getApplicationBufferSize());
                        break;
                    case BUFFER_UNDERFLOW:
                        System.out.println("BUFFER_UNDERFLOW");
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
            int mark = myAppData.position();
            myNetData.clear();
            SSLEngineResult result = engine.wrap(myAppData, myNetData);
            stream.skip(myAppData.position() - mark);
            switch (result.getStatus()) {
                case OK:
                    myNetData.flip();
                    while (myNetData.hasRemaining()) {
                        channel.write(myNetData);
                    }
                    System.out.printf("Message sent [%d] bytes to the client\n", stream.len());
                    if (stream.len() == 0) {
                        return stream.size();
                    } else {
                        break;
                    }
                case BUFFER_OVERFLOW:
                    myNetData = enlargeBuffer(myNetData, engine.getSession().getPacketBufferSize());
                    break;
                case BUFFER_UNDERFLOW:
                    throw new SSLException(
                            "Buffer underflow occured after a wrap. I don't think we should ever get here.");
                case CLOSED:
                    throw new EOFException();
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
    }
}
