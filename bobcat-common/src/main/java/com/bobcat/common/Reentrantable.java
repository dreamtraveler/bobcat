package com.bobcat.common;

@FunctionalInterface
public interface Reentrantable {
    void run(Message obj);
}