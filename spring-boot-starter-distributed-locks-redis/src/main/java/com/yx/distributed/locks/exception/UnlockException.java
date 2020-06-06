package com.yx.distributed.locks.exception;

public class UnlockException extends ExtensionException {
    public UnlockException() {
        super(ErrorCodes.UNLOCK_ERROR.getCode(), ErrorCodes.UNLOCK_ERROR.getMessage());
    }
}
