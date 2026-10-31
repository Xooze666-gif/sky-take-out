# Day 07（11-02 周一）—— 考点⑦ WebSocket（上）

## 今日任务
- [ ] 对照 `WebSocketServer.java` 全文 70 行逐行看
- [ ] 弄清三个设计决策（见下）
- [ ] 完成文末自测

## 核心讲解

### 为什么不用轮询
- 轮询：前端定时问"有新单吗"——问得勤空耗带宽，问得勤延迟高
- WebSocket：长连接，**服务端主动推**，零延迟零空转

### 三个必背设计决策
1. **为什么 ConcurrentHashMap**：连接/断开/推送在多线程并发执行，普通 HashMap 会出事
2. **为什么按 type 分组**：来单(type=1)/催单(type=2)各推各的；同一 type 下多台商家设备 → value 是 Set<Session>
3. **为什么 SESSION_POOL 是 static**：`@ServerEndpoint` 类**每个连接 new 一个实例**（JSR-356 规范），不 static 每个连接拿空池子，永远推不到别人

### Spring Boot 配套
必须注册 `ServerEndpointExporter`（见 `WebSocketConfiguration`），否则 @ServerEndpoint 不生效。

### 对应代码位置
`E:\project\sky-take-out\sky-server\src\main\java\com\sky\websocket\WebSocketServer.java`

## 自测
1. 商家电脑和平板同时登录管理端，两个都收得到来单提醒吗？靠什么机制？
2. SESSION_POOL 不声明 static，会出现什么诡异现象？
3. 为什么催单(type=2)不会推错到来单提醒(type=1)的连接上？

## 打卡
- 完成日期：____  自测是否全对：____
