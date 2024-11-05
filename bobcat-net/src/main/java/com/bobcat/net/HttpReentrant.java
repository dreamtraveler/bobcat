package com.bobcat.net;

import com.bobcat.common.Reentrant;

public abstract class HttpReentrant extends Reentrant {
    public abstract void handle(HttpClient client);
}
