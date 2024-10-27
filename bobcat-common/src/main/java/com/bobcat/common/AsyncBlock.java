package com.bobcat.common;

public class AsyncBlock implements Asyncable, Eventable {
    Eventable evNormal;
    Eventable evRequest;
    Eventable evError;
    Eventable evSuccess;
    Eventable evFinal;
    EV status = EV.EV_NORMAL;
    Message responseMsg;

    AsyncBlock(Eventable normal) {
        this.evNormal = normal;
    }

    public AsyncBlock onRequest(Eventable e) {
        this.evRequest = e;
        return this;
    }

    public AsyncBlock onError(Eventable e) {
        this.evError = e;
        return this;
    }

    public AsyncBlock onSuccess(Eventable e) {
        this.evSuccess = e;
        return this;
    }

    public AsyncBlock onFinal(Eventable e) {
        this.evFinal = e;
        return this;
    }

    public void retry(Reentrant r, int interval) {
        System.out.printf("retry set interval[%s] by [%s]\n", interval, r);
        status = EV.EV_REQUEST;
    }

    public void rewait() {
        System.out.println("rewait");
        status = EV.EV_SUCCESS;
    }

    public void finish() {
        System.out.println("finish");
        status = EV.EV_FINAL;
    }

    public EV status() {
        return status;
    }

    @Override
    public void setResponseMsg(Message obj) {
        responseMsg = obj;
    }

    @Override
    public Message getResponseMsg() {
        return responseMsg;
    }

    @Override
    public void run(Asyncable ab) {
        try {

            switch (status) {
                case EV_NORMAL: {
                    status = EV.EV_SUCCESS;
                    evNormal.run(ab);
                    if (status == EV.EV_SUCCESS && evRequest != null) {
                        evRequest.run(ab);
                    } else {
                        status = EV.EV_FINAL;
                    }
                    break;
                }
                case EV_REQUEST: {
                    status = EV.EV_SUCCESS;
                    evRequest.run(ab);
                    break;
                }
                case EV_ERROR: {
                    status = EV.EV_FINAL;
                    evError.run(ab);
                    if (evFinal != null && status == EV.EV_FINAL) {
                        evFinal.run(ab);
                    }
                    break;
                }
                case EV_SUCCESS: {
                    status = EV.EV_FINAL;
                    evSuccess.run(ab);
                    if (evFinal != null && status == EV.EV_FINAL) {
                        evFinal.run(ab);
                    }
                    break;
                }
                case EV_FINAL: {
                    break;
                }
                default:
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
