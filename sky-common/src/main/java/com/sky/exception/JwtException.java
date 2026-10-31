package com.sky.exception;

/**
 * JWT 解析/校验失败
 */
public class JwtException extends RuntimeException {

    public JwtException(String msg) {
        super(msg);
    }

    public JwtException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
