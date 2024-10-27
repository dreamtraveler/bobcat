package com.bobcat.common;

/**
 * Asyncable
 */
public interface Asyncable {
    Asyncable onRequest(Eventable e);

    Asyncable onError(Eventable e);

    Asyncable onSuccess(Eventable e);

    Asyncable onFinal(Eventable e);

    void setResponseMsg(Message obj);

    Message getResponseMsg();

    void retry(Reentrant r, int interval);

    void rewait();

    void finish();

    EV status();
}
