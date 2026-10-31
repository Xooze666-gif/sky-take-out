package com.sky.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 配置（对应 application.yml 中 sky.jwt.*）
 */
@Component
@ConfigurationProperties(prefix = "sky.jwt")
public class JwtProperties {

    /** 管理端签名密钥 */
    private String adminSecretKey;

    /** 管理端 token 有效期（毫秒） */
    private long adminTtl;

    /** 管理端 token 请求头名称 */
    private String adminTokenName;

    /** 用户端签名密钥 */
    private String userSecretKey;

    /** 用户端 token 有效期（毫秒） */
    private long userTtl;

    /** 用户端 token 请求头名称 */
    private String userTokenName;

    public String getAdminSecretKey() {
        return adminSecretKey;
    }

    public void setAdminSecretKey(String adminSecretKey) {
        this.adminSecretKey = adminSecretKey;
    }

    public long getAdminTtl() {
        return adminTtl;
    }

    public void setAdminTtl(long adminTtl) {
        this.adminTtl = adminTtl;
    }

    public String getAdminTokenName() {
        return adminTokenName;
    }

    public void setAdminTokenName(String adminTokenName) {
        this.adminTokenName = adminTokenName;
    }

    public String getUserSecretKey() {
        return userSecretKey;
    }

    public void setUserSecretKey(String userSecretKey) {
        this.userSecretKey = userSecretKey;
    }

    public long getUserTtl() {
        return userTtl;
    }

    public void setUserTtl(long userTtl) {
        this.userTtl = userTtl;
    }

    public String getUserTokenName() {
        return userTokenName;
    }

    public void setUserTokenName(String userTokenName) {
        this.userTokenName = userTokenName;
    }
}
