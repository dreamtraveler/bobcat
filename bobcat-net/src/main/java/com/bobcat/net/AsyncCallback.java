package com.bobcat.net;

import java.util.concurrent.Callable;

public class AsyncCallback<T> implements Future {
    private AsyncHandler<AsyncResult<T>> completeHandler;
    private AsyncHandler<T> successHandler;
    private AsyncHandler<Throwable> failureHandler;
    private Callable<T> supplyHandler;
    private AsyncResult<T> asyncResult;

    public AsyncCallback(Callable<T> supplyHandler) {
        this.supplyHandler = supplyHandler;
    }

    public AsyncCallback<T> onSuccess(AsyncHandler<T> handler) {
        successHandler = handler;
        return this;
    }

    public AsyncCallback<T> onFailure(AsyncHandler<Throwable> handler) {
        failureHandler = handler;
        return this;
    }

    public AsyncCallback<T> onComplete(AsyncHandler<AsyncResult<T>> handler) {
        completeHandler = handler;
        return this;
    }

    @Override
    public void applySupply() {
        if (supplyHandler != null) {
            asyncResult = new AsyncResult<>();
            try {
                T r = supplyHandler.call();
                asyncResult.setResult(r);
                asyncResult.setSucceeded(true);
            } catch (Exception e) {
                asyncResult.setCause(e);
                asyncResult.setFailed(true);
            }
        }
    }

    @Override
    public void applyCallback() {
        if (completeHandler != null) {
            completeHandler.handle(asyncResult);
        }
        if (successHandler != null && asyncResult != null && asyncResult.isSucceeded()) {
            successHandler.handle(asyncResult.getResult());
        }
        if (failureHandler != null && asyncResult != null && asyncResult.isFailed()) {
            failureHandler.handle(asyncResult.getCause());
        }
    }
}
