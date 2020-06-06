package com.yx.distributed.locks.exception;

/**
 * 加锁异常
 *
 * @author liuxingchen
 * @since 2019-12-20 09:57:43
 */
public class LockException extends ExtensionException {
    public LockException() {
        super(ErrorCodes.LOCK_ERROR.getCode(), ErrorCodes.LOCK_ERROR.getMessage());
    }
}
