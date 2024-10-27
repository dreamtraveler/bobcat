package com.bobcat.common;

import java.util.ArrayList;

public abstract class Reentrant implements Reentrantable {
    private ArrayList<AsyncBlock> course = new ArrayList<AsyncBlock>();
    private int step = 0;
    private Reentrant next = null;
    private boolean tail = false;
    private boolean isFinish = false;
    private int finishRet = 0;

    public AsyncBlock ablock(Eventable n) {
        AsyncBlock tmp = new AsyncBlock(n);
        course.add(tmp);
        return tmp;
    }

    public Reentrant setNext(Reentrant r) {
        next = r;
        return next;
    }

    protected void setTail() {
        tail = true;
    }

    protected boolean isTail() {
        return tail;
    }

    protected void finish(Asyncable ab, int ret) {
        System.out.printf("the Renntrant is be set finish ret=%d tail=%b\n", ret, tail);
        isFinish = true;
        finishRet = ret;
        if (ab != null) {
            ab.finish();
        }
    }

    protected boolean isFinish() {
        return isFinish;
    }

    @Override
    public void run(Message msg) {
        if (isFinish()) {
            return;
        }
        while (step < course.size()) {
            AsyncBlock ab = course.get(step);
            ab.setResponseMsg(msg);
            ab.run(ab);
            if (ab.status() != EV.EV_FINAL) {
                break;
            }
            if (isFinish()) {
                break;
            }
            if (step < course.size()) {
                step++;
            } else {
                break;
            }
        }
        if (isFinish()) {
            for (Reentrant reent = next; reent != null; reent = reent.next) {
                if (!reent.isFinish() && reent.isTail()) {
                    reent.finish(null, finishRet);
                    reent.run(new Message(finishRet));
                    return;
                }
            }
            return;
        }
        if (step == course.size() && next != null) {
            if (next.isTail()) {
                if (!next.isFinish()) {
                    next.finish(null, finishRet);
                    next.run(new Message(finishRet));
                }
            } else {
                next.run(msg);
            }
        }
    }
}
