package com.sky;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 苍穹外卖启动类
 * <p>
 * @EnableScheduling 开启对 @Scheduled 注解的解析。Spring Boot 不会自动开启定时任务，
 * 少了这个注解任务会静默不执行（不报错，是最容易踩的坑之一）。
 * 调度线程池大小由 spring.task.scheduling.pool.size 控制，默认为 1，
 * 多个任务会排队串行执行，长耗时任务会拖慢其他任务。
 */
@SpringBootApplication
@MapperScan("com.sky.mapper")
@EnableScheduling
public class SkyApplication {

    public static void main(String[] args) {
        SpringApplication.run(SkyApplication.class, args);
    }
}
