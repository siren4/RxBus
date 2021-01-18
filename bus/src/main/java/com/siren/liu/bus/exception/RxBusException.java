package com.siren.liu.bus.exception;

/**
 * Created by LiuG on 2019/1/14.
 */

public class RxBusException extends RuntimeException {

    private static final long serialVersionUID = -9216499463500044540L;

    public RxBusException() {
        super();
    }

    public RxBusException(String message) {
        super(message);
    }

    public RxBusException(String message, Throwable cause) {
        super(message, cause);
    }

    public RxBusException(Throwable cause) {
        super(cause);
    }

}
