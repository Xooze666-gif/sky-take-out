# Day 20（11-15 周日）—— Redis 模块学习②：Spring Data Redis 用法

## 今日任务
- [ ] 弄清 Spring Boot 里怎么用 Redis（不装环境，只看用法）
- [ ] 弄清缓存读写的标准模式（Cache-Aside，见下）
- [ ] 决策：Day21 要不要动手实现（先想好）

## 核心用法（Spring Data Redis）
```java
@Autowired
private StringRedisTemplate redisTemplate;

// 写
redisTemplate.opsForValue().set("shop:status", "1", 1, TimeUnit.HOURS);
// 读
String status = redisTemplate.opsForValue().get("shop:status");
// 删（改数据后保证一致性）
redisTemplate.delete("shop:status");
```

## Cache-Aside 模式（最常用，必背）
```
读：先查缓存 → 命中就返回；未命中查数据库 → 写入缓存 → 返回
写：先更新数据库 → 再删缓存（注意顺序，防止脏读）
```
**为什么写是"删缓存"而不是"更新缓存"**：懒加载思想，下次有人读时自然会加载最新值；更新缓存可能在并发下写回旧值。

## 环境前提（动手前确认）
Windows 装 Redis 有两条路：
1. 官方 Redis 6+ 有 Windows 版（redis-windows 项目），解压即用
2. Docker Desktop（如果装了）
都需要你手动装——AI 不代装软件，这是你的决定。

## 打卡
- 完成日期：____  Day21 决定：动手实现 / 只学到概念面试够用
