package com.bobcat.net;

@FunctionalInterface
public interface ICreateHttpHandler {
    HttpReentrant create();
}
