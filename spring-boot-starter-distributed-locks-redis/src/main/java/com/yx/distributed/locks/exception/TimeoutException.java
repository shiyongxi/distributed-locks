package com.yx.distributed.locks.exception;


public class TimeoutException extends ExtensionException {
    public TimeoutException() {
        super(ErrorCodes.TIMEOUT.getCode(), ErrorCodes.TIMEOUT.getMessage());
    }
}
