package com.yx.distributed.locks.exception;

import lombok.Getter;

@Getter
public enum ErrorCodes {
    TIMEOUT(10011001L, "系统异常，请稍后重试"),
    LOCK_ERROR(10011002L, "系统异常，请稍后重试"),
    UNLOCK_ERROR(10011003L, "系统异常，请稍后重试");

    private final Long code;

    private final String message;

    ErrorCodes(Long code, String message) {
        this.code = code;
        this.message = message;
    }
}
