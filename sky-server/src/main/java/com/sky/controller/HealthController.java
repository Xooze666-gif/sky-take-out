package com.sky.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查：验证「应用已启动 + 数据库已连通」
 */
@RestController
@RequestMapping("/health")
public class HealthController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @GetMapping
    public Map<String, Object> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("app", "sky-take-out");
        result.put("time", LocalDateTime.now().toString());
        try {
            Integer tables = jdbcTemplate.queryForObject(
                    "select count(*) from information_schema.tables where table_schema = 'sky_take_out'",
                    Integer.class);
            Integer dishes = jdbcTemplate.queryForObject("select count(*) from dish", Integer.class);
            result.put("db", "UP");
            result.put("tableCount", tables);
            result.put("dishCount", dishes);
        } catch (Exception e) {
            result.put("db", "DOWN");
            result.put("error", e.getMessage());
        }
        return result;
    }
}
