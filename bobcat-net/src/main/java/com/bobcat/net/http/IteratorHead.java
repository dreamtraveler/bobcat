package com.bobcat.net.http;

@FunctionalInterface
public interface IteratorHead {
    int apply(String key, String value);
}
