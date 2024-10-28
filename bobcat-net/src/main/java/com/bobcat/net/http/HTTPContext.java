package com.bobcat.net.http;

import java.util.ArrayList;
import java.nio.charset.StandardCharsets;

import com.bobcat.net.Stream;

public class HTTPContext {

    public class Head {
        public FieldData name = new FieldData(); // Header name
        public FieldData value = new FieldData(); // Header value
    }

    public boolean isClient = false;
    public boolean isConnect = false;

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
        isConnect = false;
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
