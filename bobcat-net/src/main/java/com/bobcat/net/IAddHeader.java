package com.bobcat.net;

@FunctionalInterface
public interface IAddHeader {
    void call(HttpClient client);
}
