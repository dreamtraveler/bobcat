package com.bobcat.net.http;

import java.nio.ByteBuffer;

@FunctionalInterface
public interface HTTPErrorCallback {
	public void cb(HTTPParser parser, String mes, ByteBuffer buf, int initial_position);
}
