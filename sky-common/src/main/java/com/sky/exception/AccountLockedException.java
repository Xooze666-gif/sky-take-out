package com.sky.exception;

import com.sky.constant.MessageConstant;

/**
 * 账号被锁定异常
 */
public class AccountLockedException extends BaseException {

    public AccountLockedException() {
        super(MessageConstant.ACCOUNT_LOCKED);
    }

    public AccountLockedException(String msg) {
        super(msg);
    }
}
