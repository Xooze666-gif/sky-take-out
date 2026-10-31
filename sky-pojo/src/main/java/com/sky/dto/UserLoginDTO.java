package com.sky.dto;

import java.io.Serializable;

/**
 * C 端用户登录入参（微信登录，模拟用 code 换 openid）
 */
public class UserLoginDTO implements Serializable {

    /** 微信授权码，模拟场景下直接用字符串标识用户 */
    private String code;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}
