# 精讲 09 · WebSocket 实时推送：来单提醒与催单

> 源码位置：`WebSocketServer` + `WebSocketConfiguration` + `OrderServiceImpl.payment/reminder`
> 八层拆解：① 场景 ② 数据 ③ 逐行 ④ 实测证据 ⑤ 权衡 ⑥ 踩坑 ⑦ 缺口 ⑧ 面试 30 秒版

---

## ① 场景

两个方向相反的业务，共用一套连接：

| 谁触发 | 触发时机 | 推给谁 | type |
|---|---|---|---|
| 用户支付成功 | `payment` 方法末尾 | 商家端 | **1 来单提醒** |
| 用户点"催单" | `reminder` 方法 | 商家端 | **2 催单** |

商家端打开管理页时，用 JS 连上 `ws://localhost:8080/ws/1`，然后**一直挂着**。用户一付款，服务端主动推一条消息过来，页面弹提示 + 响铃。

**这个需求的关键词是"实时"** —— 用户付完钱，商家要在 1 秒内知道，不能等。

---

## ② 数据：内存里发生了什么

**连接建立后**，`SESSION_POOL` 这个静态字段变成：

```
ConcurrentHashMap<String, Set<Session>>
  ├─ "1" → Set{ Session@a1b2, Session@c3d4 }     ← 两台商家设备都连了 type=1
  └─ "2" → Set{ Session@e5f6 }                    ← 一台连了 type=2
```

**连接关闭后**，对应的 `Session` 从 Set 里被移除。Set 空了，但 key 还在（这里没清理 key，是个小泄漏，见第 ⑦ 层）。

**数据库一个字节都没变。** WebSocket 是纯内存 + 网络操作，跟 MySQL 无关 —— 这也是为什么它不能靠抓 SQL 来验证。

---

## ③ 逐行拆

### `@ServerEndpoint("/ws/{type}")` —— 端点声明

```java
@Component
@ServerEndpoint("/ws/{type}")
public class WebSocketServer {
```

`{type}` 是**路径参数**，客户端连 `/ws/1` 时 `type = "1"`，连 `/ws/2` 时 `type = "2"`。

**为什么用路径参数而不是 query 参数（`/ws?type=1`）？**

因为 JSR-356（Java WebSocket 规范）的 `@PathParam` 只解析路径段。要读 query 参数得用 `session.getQueryString()` 手动解析，麻烦且容易出错。**路径段是最直接的。**

### `static` —— 这一行不加，整个功能就是坏的

```java
private static final ConcurrentHashMap<String, Set<Session>> SESSION_POOL = new ConcurrentHashMap<>();
```

**这是全篇最关键的一行。** JSR-356 规定：**`@ServerEndpoint` 标注的类，每一个连接都会创建一个新实例**（不是 Spring 的单例）。

```
连接 1 → new WebSocketServer() 实例 A
连接 2 → new WebSocketServer() 实例 B
连接 3 → new WebSocketServer() 实例 C
```

如果 `SESSION_POOL` 不是 `static`：

```
实例 A 的 sessionPool = { "1" → [Session1] }
实例 B 的 sessionPool = { "1" → [Session2] }     ← 完全独立的两个 pool
实例 C 的 sessionPool = { "1" → [Session3] }

payment 里注入的 webSocketServer（Spring 单例，第 4 个实例）
  → 它的 sessionPool 是空的
  → 推送时找不到任何会话 → 静默失败
```

**症状极其迷惑**：连接能建立（`@OnOpen` 有日志），但永远收不到消息。因为推送时用的是"另一个实例的空池子"。

**改成 `static` 后，所有实例共享同一个池子**，问题解决。

**这跟前端常见的单例 Store 是一个道理：需要一个跨实例的共享容器。**

### `ConcurrentHashMap` + `ConcurrentHashMap.newKeySet()`

```java
SESSION_POOL.computeIfAbsent(type, k -> ConcurrentHashMap.newKeySet()).add(session);
```

两个线程安全容器，为什么都要？

**`SESSION_POOL` 用 `ConcurrentHashMap`**：多个连接同时打开/关闭时，`computeIfAbsent`、`get`、`put` 会发生并发。普通 `HashMap` 在并发 `resize` 时会形成环形链表，导致 `get` 死循环（JDK7 的经典问题，JDK8 改成红黑树后不会死循环，但仍会丢数据）。

**value 用 `ConcurrentHashMap.newKeySet()` 而不是 `HashSet`**：同一个 type 下会有多个 `Session` 并发 `add`（两台设备同时连）和 `remove`（同时断）。`HashSet` 的 `add` 不是线程安全的，并发下可能丢失元素 —— **丢了 Session 意味着那台设备收不到推送，而日志上一切正常。**

**`computeIfAbsent` 为什么比 `if (map.get(k) == null) map.put(k, newSet())` 好？**

后者是 check-then-act，两个连接同时进来会创建两个 Set，一个被覆盖，**丢掉一个连接**。`computeIfAbsent` 在 `ConcurrentHashMap` 里是原子的。

**这一行代码同时防住了三个并发问题。** 面试问"你的 WebSocket 会话池怎么保证线程安全"，这就是完整答案。

### `sendToType`：推送 + 空池保护

```java
public void sendToType(String type, String message) {
    Set<Session> sessions = SESSION_POOL.get(type);
    if (sessions == null || sessions.isEmpty()) {
        log.info("无 type={} 的在线连接，跳过推送", type);
        return;                                   // ← 关键：不抛异常
    }
    for (Session session : sessions) {
        try {
            session.getBasicRemote().sendText(message);
        } catch (Exception e) {
            log.error("WebSocket 推送失败", e);   // ← 单条失败不影响其他
        }
    }
}
```

**两个"吞异常"的设计，都是有意的：**

**（1）没有连接时返回，不抛异常。**

支付成功是核心业务，WebSocket 推送是"锦上添花"。如果商家没开页面（没有连接），支付流程**绝不能因此失败**。

```java
// payment 里
orderMapper.updateById(order);
webSocketServer.sendToType("1", "...");   // 如果这里抛异常，前面的事务会回滚！
```

因为 `payment` 方法有 `@Transactional`（或者说，即使没有事务，异常也会让接口返回 500，用户看到"支付失败"，但钱已经扣了）。

**所以"推送失败不影响主流程"是必须的。**

**（2）单条推送失败只记日志，继续推下一个。**

如果某个 Session 已经半断开（TCP 还没超时但对方已经没了），`sendText` 会抛 `IOException`。这时候应该：跳过它，继续推给其他设备。

**如果把 `try-catch` 放在 `for` 循环外面，一个坏连接会导致后面所有设备都收不到。**

### `getBasicRemote()` vs `getAsyncRemote()`

```java
session.getBasicRemote().sendText(message);
```

`getBasicRemote()` 是**阻塞式**发送：调用线程会等消息写完才返回。

`getAsyncRemote()` 是非阻塞：立即返回，发送在另一个线程完成。

**这里为什么用 Basic？** 因为消息很小（几十字节），阻塞时间可以忽略。而 Async 要注意"连续调用 `sendText` 会抛 `IllegalStateException`（上一个还没发完）"，需要额外的队列管理。

**但如果并发量大**（比如一次推给 1000 个连接），Basic 会串行阻塞，`sendToType` 的调用线程（也就是 HTTP 请求线程）会被拖慢。

**改进方向**：把推送放到单独的线程池，或者用 Async + 队列。

### `WebSocketConfiguration`：为什么必须注册 `ServerEndpointExporter`

```java
@Configuration
public class WebSocketConfiguration {
    @Bean
    public ServerEndpointExporter serverEndpointExporter() {
        return new ServerEndpointExporter();
    }
}
```

**没有这个 Bean，`@ServerEndpoint` 完全不生效。**

原因：JSR-356 的设计是"由 Servlet 容器（Tomcat）扫描并注册端点"。但 Spring Boot 用的是**内嵌 Tomcat**，容器的启动时机和 Spring 的 Bean 扫描对不上——容器启动时，Spring 还没扫到你的 `@ServerEndpoint` 类。

`ServerEndpointExporter` 的作用是：**在 Spring 容器初始化完后，主动把所有 `@ServerEndpoint` Bean 注册到 Tomcat 的 `ServerContainer` 里。**

**注意**：如果打成 war 部署到外部 Tomcat，**不能**加这个 Bean（会和容器自己的扫描冲突，报"端点重复注册"）。这是"内嵌容器"和"外部容器"的一个分水岭。

---

## ④ 实测证据（这次不是 SQL，是真实连接）

WebSocket 抓不了 SQL，所以我写了两个 Node 客户端真连了一次。Node 22 内置了全局 `WebSocket`，不用装任何包。

### 实验 1：验证「按 type 分组」

两个客户端分别连 `/ws/1` 和 `/ws/2`，然后触发一次**催单**（应该只推给 type=2）。

脚本（`docs/_ws_test.js`）：

```js
const a = new WebSocket('ws://localhost:8080/ws/1');
const b = new WebSocket('ws://localhost:8080/ws/2');
a.onmessage = e => console.log(`[type=1] 收到 -> ${e.data}`);
b.onmessage = e => console.log(`[type=2] 收到 -> ${e.data}`);
```

触发：

```bash
curl -s "http://localhost:8080/user/order/reminder/4" -H "authentication: $UT"
```

**实际输出：**

```
[+0.06s] 客户端(type=2) 已连接
[+0.06s] 客户端(type=1) 已连接
[+5.01s] 客户端(type=2) 收到推送 -> {"type":2,"orderId":4,"content":"订单号 20261018141859582146"}
测试结束
```

**关键：type=1 的客户端收到了"已连接"，但没收到任何消息。**

这就是"按 type 分组"的直接证据：催单只推给 `type=2`，连了 `type=1` 的设备一条都收不到。

**如果没分组（所有连接都推），商家页面会同时收到"来单提醒"和"催单"，两类消息混在一起，JS 只能靠消息体里的 `type` 字段自己分——那还不如分组。**

### 实验 2：验证「支付成功 → 来单推送」

只连 `type=1`，然后真实支付一笔订单（订单号 `20261027224405654350`）。

**实际输出：**

```
[+0.04s] 商家端(type=1) 已连接
[+8.79s] 商家端收到来单提醒 -> {"type":1,"orderId":6,"content":"订单号 20261027224405654350"}
```

**从支付请求发出到商家收到推送，延迟在毫秒级。** 对比轮询：如果 5 秒轮一次，平均延迟 2.5 秒，最坏 5 秒。

### 实验 3：验证「无连接时的保护」

把两个客户端都关掉，再触发一次催单。响应依然是：

```json
{"code":1,"msg":null,"data":null}
```

**接口没报错。** 应用日志里有：

```
22:44:05  INFO  WebSocketServer : 无 type=2 的在线连接，跳过推送
```

（日志里中文是乱码 `�� type=2 ���������ӣ���������`，是 Windows CMD 编码问题，见第 ⑥ 层。）

**这条日志是"推送失败不影响主流程"的证据**：没有连接，`sendToType` 直接 return，`reminder` 方法正常返回成功。

### 实验 4：`@OnOpen` 的计数日志

```
22:43:36  INFO  WebSocketServer : WebSocket 新连接，type=2，当前连接数=1
22:43:36  INFO  WebSocketServer : WebSocket 新连接，type=1，当前连接数=1
22:44:15  INFO  WebSocketServer : WebSocket 新连接，type=1，当前连接数=1
```

**注意第三次的"当前连接数=1"。** 之前 type=1 已经连过一次（第一个脚本），断开后应该被 `@OnClose` 移除。现在新连一个是 1，说明 **`@OnClose` 的清理逻辑生效了**。

**如果 `@OnClose` 没生效，这里会显示 2（累积）** —— 时间长了内存里会堆满失效的 Session。

---

## ⑤ 权衡

### WebSocket vs 轮询 vs SSE

| | **WebSocket（当前）** | 短轮询 | 长轮询 | SSE |
|---|---|---|---|---|
| 方向 | **双向** | 客户端单向 | 客户端单向（延迟响应） | 服务端单向 |
| 协议 | `ws://`（HTTP Upgrade） | HTTP | HTTP | HTTP |
| 延迟 | **毫秒级** | 轮询间隔/2 | 毫秒级 | 毫秒级 |
| 空转开销 | 无 | **高**（大量空响应） | 中 | 无 |
| 浏览器支持 | 好 | 全部 | 全部 | 好（IE 不支持） |
| 断线重连 | **要自己写** | 天然 | 天然 | 浏览器自动 |
| 服务端复杂度 | 中（会话管理） | 低 | 中（挂起线程） | 低 |

**为什么选 WebSocket？**

1. **需求是双向的**：商家端不只是"收消息"，将来还要"发指令"（比如确认接单直接走 WS）。SSE 只能服务端→客户端。
2. **延迟要求高**：外卖来单提醒，晚 5 秒可能就被别家抢单了。
3. **轮询的空转成本**：1000 个商家，每 2 秒轮一次 = 每秒 500 个请求，其中 99% 的回答是"没有新订单"。这 500 QPS 纯粹是浪费。

**如果只是"服务端推、客户端收"，其实 SSE 更简单**（不用管连接升级、不用写重连、自动走 HTTP/2 多路复用）。但商家端要双向，所以 WebSocket 更合适。

**长轮询其实是个不错的折中**：客户端发请求，服务端"挂住"不返回，等有新消息或者 30 秒超时才返回。延迟和 WebSocket 差不多，兼容性更好。缺点是占线程（Tomcat 200 个线程，最多挂 200 个商家）。

### 为什么按 type 分组，而不是所有连接都推？

三个理由：

1. **业务语义清晰**：`type=1` 是来单，`type=2` 是催单。两种消息的 UI 表现完全不同（一个弹窗+响铃，一个红点提示）。
2. **客户端可以按需订阅**：如果将来加了 `type=3`（库存预警），只有关心库存的页面去连，不会收到无关消息。
3. **广播范围可控**：出问题好排查（"只有催单收不到" vs "所有推送都收不到"）。

**代价**：客户端要维护多个连接（如果同时订阅多种消息）。现在只有两种，商家端一般只连 `type=1`（来单最重要），催单走轮询或者也用 1。

**更优雅的设计**：一个连接，消息体里带 `type` 字段，客户端自己分发。这样只需一条连接，但服务端要维护"这个连接订阅了哪些 type"的映射。

现在这版是"一个连接对应一个 type"，简单但不灵活。

### 为什么 `Set<Session>` 而不是单个 `Session`？

**同一个 type 下会有多个连接**：

- 商家的电脑开着管理页（收来单）
- 商家的平板也开着（备用）
- 商家的手机小程序也开着

**三台设备都要收到提醒。** 如果 value 是单个 `Session`，后连的会覆盖前面的 —— 只有最后连的那台能收到。

**这也是"多端登录"的通用处理方式：一个用户/一个业务频道可以有多条连接，推送时遍历。**

---

## ⑥ 踩坑实录

### 坑 1：会话池不是 `static`

前面讲过了，但值得再说一次，因为**它的表现是"完全静默失败"**：

- 连接能建立，`@OnOpen` 日志正常
- 客户端显示"已连接"
- 支付正常
- **但消息永远不到**

第一反应会去查"是不是广播写错了""是不是前端 `onmessage` 没绑"。真正的原因是：**你推送的实例和你连接时的实例不是同一个**。

**这个坑的教训：JSR-356 的端点类是"每连接一实例"，任何跨连接的状态都必须 `static`（或者放在 Spring 单例 Bean 里）。**

### 坑 2：没加 `ServerEndpointExporter`，连接直接 404

第一版没写 `WebSocketConfiguration`。前端 `new WebSocket('ws://localhost:8080/ws/1')` 立刻 `onclose`，浏览器 Network 面板显示 `101 Switching Protocols` 失败，返回 404。

**404 而不是 500，很有迷惑性** —— 看起来像"路径写错了"。实际是端点根本没被注册，Tomcat 不知道 `/ws/1` 是什么。

**判断方法**：看启动日志。注册成功的话，`ServerEndpointExporter` 会输出类似 `Registering @ServerEndpoint annotated classes` 的日志；没有这行说明 Bean 没生效。

### 坑 3：日志中文乱码，看不出推送成功还是失败

```
22:44:05  INFO  WebSocketServer : �� type=2 ���������ӣ���������
```

Windows CMD 默认代码页 936（GBK），JVM 输出 UTF-8 字节被按 GBK 解码，就成了乱码。

**影响很大**：一条"跳过推送"和一条"推送失败"在乱码下看起来一样，排查时完全瞎猜。

**三种解法**：
1. `chcp 65001` 切成 UTF-8（临时）
2. 启动参数加 `-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8`（JDK18+ 需要 `stdout.encoding`）
3. **用 IDEA 的控制台看**（默认 UTF-8，最简单）

### 坑 4：我以为要装 `spring-boot-starter-websocket`

不用。Spring Boot 已经把 `tomcat-embed-websocket` 作为 web starter 的传递依赖带进来了。

要写 **STOMP 那种基于消息代理的 WebSocket**（`@MessageMapping`、`SimpMessagingTemplate`）才需要 `spring-boot-starter-websocket`。

**这个项目用的是 JSR-356 原生注解**（`@ServerEndpoint`），零额外依赖。

**两者的区别值得知道：**

| | JSR-356 原生（当前） | Spring STOMP |
|---|---|---|
| 依赖 | 无（web starter 自带） | `spring-boot-starter-websocket` |
| 协议 | 裸 WebSocket | WebSocket + STOMP 子协议 |
| 消息路由 | 自己按 type 分发 | `@MessageMapping("/order")` + `@SendTo` |
| 广播 | 自己维护会话池 | `SimpMessagingTemplate.convertAndSend("/topic/orders", ...)` |
| 订阅模型 | 无（连上就收） | 有（客户端订阅 topic） |
| 适合 | 简单推送（1~2 种消息） | 复杂消息路由（多种 topic、点对点） |

**这个项目只有 2 种消息、1 个接收方（商家端），JSR-356 足够。** 如果要做"每个商家只看自己的订单"，STOMP 的 `@SendToUser` 会方便很多。

---

## ⑦ 缺口

| 缺口 | 严重度 | 说明 | 怎么改 |
|---|---|---|---|
| **WebSocket 端点没有鉴权** | **高** | 任何人 `new WebSocket('ws://你的域名/ws/1')` 就能收到**所有**来单提醒（含订单号） | 连接时校验 token：`/ws/{type}?token=xxx`，`@OnOpen` 里 `parseJwt` 验签 |
| **推送是全局广播，不区分商家** | 高 | 现在是"所有连 type=1 的都收到所有订单"。多商家场景下，A 店会收到 B 店的订单 | 会话池按 `shopId` 分组：`Map<shopId, Set<Session>>` |
| **单机内存池，多实例不共享** | 中 | 部署 2 台服务器，用户支付请求落在 A 机，商家连在 B 机 → 收不到 | Redis Pub/Sub 或 MQ 广播 |
| **没有心跳，连接会被中间设备掐断** | 中 | Nginx 默认 60s 无数据就断；云厂商 SLB 也有空闲超时 | 客户端每 30s 发 ping，服务端回 pong；或服务端定时发心跳帧 |
| **空 Set 的 key 不清理** | 低 | `@OnClose` 只 remove Session，不动 key。长时间运行会积累空 Set | `if (sessions.isEmpty()) SESSION_POOL.remove(type)` |
| **同步发送，大并发会阻塞 HTTP 线程** | 中 | `getBasicRemote().sendText` 串行 | 异步线程池推送，或 `getAsyncRemote()` + 队列 |
| **前端没有自动重连** | 中 | 网络抖动断开后，页面永远收不到消息，除非刷新 | `onclose` 里 `setTimeout(reconnect, 3000)` |
| **消息体是手拼字符串** | 低 | `"{\"type\":1,\"orderId\":" + id + ",...}"`，字段多了容易拼错 | 用 `ObjectMapper.writeValueAsString(dto)` |

### 「WebSocket 没鉴权」值得单独展开

这是本项目**继"MD5 无盐"之后第二个高危点**。

**为什么拦截器管不到 WebSocket？** 因为 `WebMvcConfiguration` 里的拦截器是 **Spring MVC 的 HandlerInterceptor**，只对经过 `DispatcherServlet` 的请求生效。而 WebSocket 的连接建立走的是 **HTTP Upgrade 握手 + Tomcat 的 WebSocket 容器**，**根本不经过 DispatcherServlet**。

验证方法：不登录、不带任何 token，直接连：

```js
new WebSocket('ws://localhost:8080/ws/1')   // 能连上，还能收到所有订单推送
```

**这意味着任何知道地址的人都能实时窃取商家的订单信息（订单号、订单 ID）。** 订单号可以拿去查详情（`GET /admin/order/details/{id}` 需要 token，但订单号本身是敏感信息）。

**修法（30 秒内能说清）：**

```java
@OnOpen
public void onOpen(Session session, @PathParam("type") String type) throws IOException {
    // 从 query string 取 token：ws://host/ws/1?token=xxx
    String query = session.getQueryString();          // "token=eyJ..."
    String token = parseParam(query, "token");
    if (token == null) {
        session.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, "未登录"));
        return;
    }
    try {
        Map<String, Object> claims = JwtUtil.parseJwt(adminSecretKey, token);
        Long empId = ((Number) claims.get("empId")).longValue();
        // 把 empId 绑定到 session，后续推送可按商家过滤
        session.getUserProperties().put("empId", empId);
    } catch (JwtException e) {
        session.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, "token 无效"));
    }
}
```

**注意一个细节：浏览器的 WebSocket API 不支持自定义请求头**（不能像 `fetch` 那样加 `token: xxx`）。所以 token 只能放在 **URL query** 或 **子协议（`Sec-WebSocket-Protocol`）** 里。

放 URL 的问题是 token 会进 access log（Nginx 的 `$request` 里有完整 URL）。生产上更常见的是用 subprotocol 或者"连上后先发一条 auth 消息，鉴权通过前不推送"。

---

## ⑧ 面试 30 秒版

> 商家端要实时收来单提醒，轮询延迟高、空转大，所以用 WebSocket 长连接。端点 `/ws/{type}`，`type=1` 来单、`type=2` 催单，用 `@PathParam` 拿 type，比解析 query string 干净。
>
> 会话池 `SESSION_POOL` 必须是 **static** —— JSR-356 规定每个连接都 new 一个端点实例，不 static 的话每个连接有自己独立的空池子，推送永远找不到会话，而且症状是静默失败、连接看起来完全正常。value 用 `ConcurrentHashMap.newKeySet()` 是因为一个 type 下有多台设备并发连、并发断。
>
> 我实测过：两个客户端分别连 type=1 和 type=2，触发催单，只有 type=2 收到，type=1 一条没收到，证明分组隔离生效。
>
> 工程上两个关键设计：一是没有连接时直接 return 不抛异常，因为推送是锦上添花，不能让它把支付主流程搞挂；二是单个 Session 发送失败只记日志继续推下一个，否则一个僵死连接会拖累所有设备。
>
> 已知最大问题是 **WebSocket 端点没有鉴权** —— MVC 拦截器管不到 WebSocket 握手，任何人连 `/ws/1` 就能收到全部订单推送。要修的话在 `@OnOpen` 里从 query string 或 subprotocol 取 token 验签，不通过直接 close。
