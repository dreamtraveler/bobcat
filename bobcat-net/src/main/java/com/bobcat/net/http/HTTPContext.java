package com.bobcat.net.http;

import java.util.ArrayList;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;

import com.bobcat.net.Stream;

public class HTTPContext {

    public class Head {
        public FieldData name = new FieldData(); // Header name
        public FieldData value = new FieldData(); // Header value
    }

    public enum HPhase {
        FIELD, VALUE;
    }

    public HPhase state = HPhase.VALUE;
    public int completed = 0;
    public ArrayList<Head> headers = new ArrayList<>();
    public FieldData body = new FieldData();
    public FieldData url = new FieldData();
    public Stream stream = new Stream(1024);

    public Head addHead() {
        Head h = new Head();
        headers.add(h);
        return h;
    }

    public Head getLastHead() {
        return headers.get(headers.size() - 1);
    }

    public void reset() {
        state = HPhase.VALUE;
        completed = 0;
        headers.clear();
        body.reset();
        url.reset();
        stream.reset();
    }

    public String getUrl() {
        return StandardCharsets.UTF_8.decode(stream.slice(url.off, url.len)).toString();
    }

    public String getQuery() {
        String url = getUrl();
        int pos = url.indexOf("?");
        if (pos == -1 || pos + 1 >= url.length()) {
            return "";
        } else {
            return url.substring(pos + 1);
        }
    }

    public String getPath() {
        String url = getUrl();
        int pos = url.indexOf("?");
        if (pos == -1) {
            return url;
        } else {
            return url.substring(0, pos);
        }
    }

    public String getBody() {
        return StandardCharsets.UTF_8.decode(stream.slice(body.off, body.len)).toString();
    }

    public ByteBuffer getBodyBuf() {
        return stream.slice(body.off, body.len);
    }

    public void iterator(IteratorHead func) {
        for (Head h : headers) {
            if (h.name.len > 0 && h.value.len > 0) {
                int ret = func.apply(StandardCharsets.UTF_8.decode(stream.slice(h.name.off, h.name.len)).toString(),
                        StandardCharsets.UTF_8.decode(stream.slice(h.value.off, h.value.len)).toString());
                if (ret != 0) {
                    break;
                }
            }
        }
    }

    public void destory() {
        reset();
        state = null;
        headers = null;
        body = null;
        url = null;
        stream.destory();
        stream = null;
    }
}
