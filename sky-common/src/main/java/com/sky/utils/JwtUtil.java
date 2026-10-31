package com.sky.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.exception.JwtException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 手写 JWT 工具（HS256），零第三方依赖
 * <p>
 * 结构：base64Url(header) . base64Url(payload) . base64Url(signature)
 * 签名 = HMAC-SHA256(前两段, secretKey)
 * <p>
 * 为什么不用 jjwt：这里只需 HS256 一种算法，用 JDK 自带的 javax.crypto 足够，
 * 少一个依赖，也便于讲清“签名到底签的是什么”。
 */
public class JwtUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String HMAC_ALG = "HmacSHA256";
    /** 固定头部 {"alg":"HS256","typ":"JWT"} 的 Base64Url 编码 */
    private static final String HEADER_PART = encode("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));

    private JwtUtil() {
    }

    /**
     * 生成 token
     *
     * @param secretKey 签名密钥
     * @param ttlMillis 有效期（毫秒）
     * @param claims    自定义载荷
     */
    public static String createJwt(String secretKey, long ttlMillis, Map<String, Object> claims) {
        if (secretKey == null || secretKey.isEmpty()) {
            throw new JwtException("secretKey 不能为空");
        }
        long now = System.currentTimeMillis();
        Map<String, Object> payload = new LinkedHashMap<>();
        if (claims != null) {
            payload.putAll(claims);
        }
        payload.put("iat", now / 1000);
        payload.put("exp", (now + ttlMillis) / 1000);

        String payloadPart;
        try {
            payloadPart = encode(MAPPER.writeValueAsBytes(payload));
        } catch (IOException e) {
            throw new JwtException("payload 序列化失败", e);
        }
        String content = HEADER_PART + "." + payloadPart;
        return content + "." + encode(sign(secretKey, content));
    }

    /**
     * 解析并校验 token
     *
     * @return 载荷（含 exp 校验通过后的自定义字段）
     */
    public static Map<String, Object> parseJwt(String secretKey, String token) {
        if (token == null || token.isEmpty()) {
            throw new JwtException("token 为空");
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new JwtException("token 格式非法");
        }

        String content = parts[0] + "." + parts[1];
        byte[] expected = sign(secretKey, content);
        byte[] actual = decode(parts[2]);

        // 常量时间比较，避免时序攻击（普通 equals 会因为提前返回泄露前缀信息）
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new JwtException("token 签名不合法");
        }

        Map<String, Object> payload;
        try {
            payload = MAPPER.readValue(decode(parts[1]), new TypeReference<Map<String, Object>>() {
            });
        } catch (IOException e) {
            throw new JwtException("token 载荷解析失败", e);
        }

        Object exp = payload.get("exp");
        if (exp == null) {
            throw new JwtException("token 缺少 exp");
        }
        long expSeconds = ((Number) exp).longValue();
        if (System.currentTimeMillis() / 1000 > expSeconds) {
            throw new JwtException("token 已过期");
        }
        return payload;
    }

    private static byte[] sign(String secretKey, String content) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALG);
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), HMAC_ALG));
            return mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new JwtException("签名计算失败", e);
        }
    }

    private static String encode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static byte[] decode(String s) {
        try {
            return Base64.getUrlDecoder().decode(s);
        } catch (IllegalArgumentException e) {
            throw new JwtException("Base64Url 解码失败", e);
        }
    }
}
