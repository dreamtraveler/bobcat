package com.bobcat.net.http;

@FunctionalInterface
public interface HTTPCallback {
	public int cb(HTTPParser parser);
}
