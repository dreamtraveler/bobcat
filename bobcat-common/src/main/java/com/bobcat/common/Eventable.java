package com.bobcat.common;

@FunctionalInterface
public interface Eventable {
   void run(Asyncable ab);
}

enum EV {
   EV_NORMAL, EV_REQUEST, EV_ERROR, EV_SUCCESS, EV_FINAL;
}
