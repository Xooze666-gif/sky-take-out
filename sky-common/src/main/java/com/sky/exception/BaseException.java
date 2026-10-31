package com.sky.exception;

/**
 * 业务异常基类
 * <p>
 * 继承 RuntimeException 而非 Exception：
 * 事务默认只对 RuntimeException 回滚；且业务异常不该强迫调用方 try-catch，
 * 统一由 GlobalExceptionHandler 兜底转成 JSON。
 */
public class BaseException extends RuntimeException {

    public BaseException() {
    }

    public BaseException(String msg) {
        super(msg);
    }
}
