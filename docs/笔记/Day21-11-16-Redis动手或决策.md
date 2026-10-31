# Day 21（11-16 周一）—— Redis 模块：动手实现或巩固决策

## 分支 A：决定动手（需先自行装好 Redis 并启动，root 无密码 6379）
- [ ] sky-server/pom.xml 加 spring-boot-starter-data-redis（联网下载，用户已授权先例）
- [ ] application.yml 配 redis 连接
- [ ] 实现「店铺状态缓存」：user 端查店铺状态走缓存，admin 端改状态时删缓存
- [ ] 实现「热门菜品缓存」：user 端按分类查菜品走缓存
- [ ] 加 @Cacheable / 手写 Cache-Aside 二选一，能讲清为什么
- [ ] curl 实测：改状态后缓存是否失效；直接 redis-cli 看 key

## 分支 B：不动手（面试话术路线）
- [ ] 把 Q15 话术打磨到脱口而出："本机没装 Redis 用了状态机+唯一索引；生产会加 Redis 缓存热菜和店铺状态，用 Cache-Aside 模式，写操作先更新库再删缓存"
- [ ] 能画出店铺状态加缓存后的读写流程图
- [ ] 能答：加了 Redis 之后新的故障点是什么？（答：Redis 挂了 → 降级方案：缓存穿透到数据库 + 限流）

## 无论哪个分支
- [ ] 更新简历（如果做了 Redis：技术栈加 Redis，项目描述加一条缓存设计）

## 打卡
- 完成日期：____  走哪个分支：____
