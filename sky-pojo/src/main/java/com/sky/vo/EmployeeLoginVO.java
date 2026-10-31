package com.sky.vo;

import java.io.Serializable;

/**
 * 员工登录返回
 */
public class EmployeeLoginVO implements Serializable {

    private Long id;

    private String userName;

    private String name;

    /** 签发的 JWT */
    private String token;

    public EmployeeLoginVO() {
    }

    public EmployeeLoginVO(Long id, String userName, String name, String token) {
        this.id = id;
        this.userName = userName;
        this.name = name;
        this.token = token;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}
