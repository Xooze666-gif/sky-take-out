# 黑马程序员苍穹外卖

一个面向**简历与求职**、从零独立开发的外卖点餐系统后端工程（Spring Boot 3 + MyBatis-Plus + MySQL），并配套最小管理端前端用于演示。

> 说明：本项目不直接搬运任何课程源码，按真实外卖系统的业务与工程结构逐步实现，
> 重点覆盖面试高频考点：JWT 鉴权、ThreadLocal 上下文、公共字段自动填充、
> 关联表与事务、支付回调幂等、WebSocket 实时推送等。

## 技术栈

| 层 | 技术 |
|---|---|
| 语言 | Java 17 |
| 框架 | Spring Boot 3.2.5、Spring MVC |
| ORM | MyBatis-Plus 3.5.5 |
| 数据库 | MySQL 8.0（HikariCP 连接池） |
| 鉴权 | 手写 JWT（HS256，零第三方依赖） |
| 报表导出 | Apache POI 5.2.5（xlsx） |
| 定时任务 | Spring Task（`@Scheduled` + cron 配置化） |
| 构建 | Maven 3.8.6 |

## 模块结构

```
sky-take-out
├── sky-common   公共层：常量 / 异常 / 工具 / ThreadLocal 上下文 / 统一返回
├── sky-pojo     实体层：entity / dto / vo
├── sky-server   服务层：controller / service / mapper / config / interceptor
└── db           sky_take_out.sql（建表 + 种子数据）
```

## 快速开始

1. 启动 MySQL80 服务（需管理员权限）
2. 导入数据库：`mysql -uroot -p123456 < db/sky_take_out.sql`
3. 构建：
   ```bash
   export JAVA_HOME=/d/env/jdk-17
   /d/env/apache-maven-3.8.6/bin/mvn -gs maven-settings.xml -f pom.xml package -DskipTests
   ```
4. 启动：
   ```bash
   /d/env/jdk-17/bin/java.exe -jar sky-server/target/sky-server-1.0-SNAPSHOT.jar --server.port=8080
   ```
5. 浏览器访问管理端：<http://localhost:8080/>（前端为纯静态 SPA，已随 jar 打包）

## 默认账号

管理端：`admin / 123456`

## 已实现模块

- [x] 员工登录（JWT + 拦截器 + ThreadLocal）
- [x] 员工管理（分页 / 启停 / 新增 / 修改）
- [x] 分类管理
- [x] 菜品管理（含口味一对多、图片上传）
- [x] 套餐管理（多对多 + 事务 + 起售校验）
- [x] 用户端（微信登录模拟 + 购物车）
- [x] 订单下单 + 支付回调幂等
- [x] WebSocket 来单提醒 + 商家端订单管理
- [x] 定时任务（待付款超时自动取消 / 派送中超时自动完成，条件更新防并发误改）
- [x] 统计报表 + Excel 导出（营业额 / 用户 / 订单 / 销量 Top10）
- [x] 最小前端管理页（纯静态 SPA，随 jar 打包，访问 http://localhost:8080/）
- [x] 简历项目描述（`docs/简历项目描述.md`）
- [x] 面试 20 问（`docs/面试20问.md`）
