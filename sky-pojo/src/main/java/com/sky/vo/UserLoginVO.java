package com.sky.vo;

import java.io.Serializable;

/**
 * C 端用户登录返回
 */
public class UserLoginVO implements Serializable {

    private Long id;

    private String openid;

    private String token;

    public UserLoginVO() {
    }

    public UserLoginVO(Long id, String openid, String token) {
        this.id = id;
        this.openid = openid;
        this.token = token;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOpenid() {
        return openid;
    }

    public void setOpenid(String openid) {
        this.openid = openid;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}
