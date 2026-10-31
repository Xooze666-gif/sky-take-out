# 精讲 05 · 拦截器与 ThreadLocal：登录态怎么传遍全链路

> 源码位置：`WebMvcConfiguration.addInterceptors` → `JwtTokenAdminInterceptor` → `BaseContext`
> 八层拆解：① 场景 ② 数据 ③ 逐行 ④ SQL 实况 ⑤ 权衡 ⑥ 踩坑 ⑦ 缺口 ⑧ 面试 30 秒版

---

## ① 场景

登录已经拿到 token。现在管理员点「订单管理」，前端发出：

```
GET /admin/order/conditionSearch?page=1&pageSize=10
token: eyJhbGciOiJIUzI1NiIs...
```

这个请求要经过 **7 个 Controller 里的任意一个之前**，先被一道关卡拦下。这道关卡只干三件事：**验票、取人、存兜里**。

---

## ② 数据：执行前 → 执行后

**执行前**：线程 `http-nio-8080-exec-3` 的 ThreadLocal 是**空的**（上一次请求结束被清掉了）。

**执行后（preHandle 里）**：

```
Thread[http-nio-8080-exec-3]
  └─ ThreadLocalMap
       └─ key: BaseContext 的 ThreadLocal 实例
          value: 1L          ← 从 token 里解出来的 empId
```

**请求结束后（afterCompletion 里）**：这个 entry 被 `remove()` 掉，回到空。

**数据库一个字节都没变。** 拦截器全程不碰数据库——它只做密码学运算（HMAC-SHA256）和内存操作。这是理解 JWT 鉴权性能优势的关键：**每次请求 0 次数据库查询。**

---

## ③ 逐行拆

### 第 1 行：先判断"这是不是 Controller 方法"

```java
if (!(handler instanceof HandlerMethod)) {
    return true;
}
```

`handler` 是 Spring MVC 交给拦截器的"当前处理器"。它可能是：
- `HandlerMethod` → 你写的 `@RequestMapping` 方法（**需要鉴权**）
- `ResourceHttpRequestHandler` → 静态资源（`/index.html`、`/images/xx.jpg`）
- `DefaultServletHttpRequestHandler` → 容器默认 Servlet

**为什么这行必须有？** 虽然 `WebMvcConfiguration` 里已经限定 `addPathPatterns("/admin/**")`，静态资源本来就不在这个范围内，但**这不代表以后不会有人把拦截器加到 `/**`**。加了这行是兜底：即使路径匹配上了静态资源，也不会因为"没有 token"而 401，导致页面白屏 CSS 丢失。

这也解释了为什么访问 `http://localhost:8080/`（前端首页）不需要登录——它压根不在 `/admin/**` 或 `/user/**` 里。

### 第 2 行：取票

```java
String token = request.getHeader(jwtProperties.getAdminTokenName());
```

`adminTokenName` 的值来自 yml：`token`。

**为什么从配置读，不写死？** 因为用户端用的是 `authentication`。如果写死，两端就得用同一个头名，虽然技术上能跑，但一旦同一个客户端（比如浏览器）同时登录了后台和用户端，两个 token 只能存一个，直接互相覆盖。

**用两个不同的请求头名，"同一浏览器同时登两端"才成立。**

### 第 3 行：没票直接 401，且返回 `false`

```java
if (token == null || token.isBlank()) {
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);   // 401
    return false;
}
```

`return false` 的语义是**中断请求**：后续的 Controller 不会执行，`afterCompletion` 会对**已经通过的**拦截器执行（本拦截器因为 preHandle 没走完，自己的 afterCompletion 不被调用）。

`token.isBlank()` 挡的是 `" "`（只有空格）。只判 `null` 的话，前端如果传个空字符串头（某些 HTTP 客户端会自动加空 header），`parseJwt` 走到 `token.split("\\.")` 得 `length == 1`，抛 `JwtException("token 格式非法")`，也被 catch 了——所以功能上不影响，但日志里的错误原因会从"未携带"变成"格式非法"，排查时容易误判。

### 第 4 行：验签 + 取人

```java
Map<String, Object> claims = JwtUtil.parseJwt(jwtProperties.getAdminSecretKey(), token);
Object empId = claims.get(JwtClaimsConstant.EMP_ID);
if (empId == null) {
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    return false;
}
BaseContext.setCurrentId(((Number) empId).longValue());
```

三个细节值得记：

**（a）`empId == null` 为什么单独判？** 因为 `parseJwt` 只保证"签名对、没过期"，**不保证载荷里有 `empId`**。如果攻击者用泄露的密钥签一个只有 `iat`/`exp` 的 token，签名是合法的，但 `empId` 是 null，`((Number) null).longValue()` 直接 NPE → 500。判空把这种情况变成干净的 401。

**（b）为什么是 `((Number) empId).longValue()` 而不是 `(Long) empId`？**

因为 JSON 反序列化后，小整数会被还原成 `Integer` 而不是 `Long`！`empId = 1` 在 `Map<String,Object>` 里是 `Integer`，直接 `(Long)` 强转会 `ClassCastException`。

用 `Number` 接收再 `.longValue()` 是**唯一安全**的写法。这个坑我第一版就踩了——登录能成功，一调需要鉴权的接口就 500，报 `java.lang.Integer cannot be cast to java.lang.Long`。

**（c）`JwtClaimsConstant.EMP_ID` 是什么？** 就是一个字符串常量 `"empId"`。抽成常量是为了让 Controller 里的 `claims.put(...)` 和拦截器里的 `claims.get(...)` 用**同一个符号**，不会因为一边写 `"empId"`、一边写 `"empID"` 而出错。这种错误编译期不报，运行期静默返回 null，非常难查。

### 第 5 行：`afterCompletion` —— 最容易被漏掉的一行

```java
@Override
public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                            Object handler, Exception ex) {
    BaseContext.removeCurrentId();
}
```

**为什么必须清？** 这里的逻辑链是这样的：

```
Tomcat 线程池（默认 200 个线程，不是请求结束就销毁）
  ↓
请求 A 用线程 3 → ThreadLocal 存了 id=1 → 请求结束，线程 3 归还池子
  ↓
请求 B 用线程 3 → ThreadLocal 里还是 id=1（没清）
  ↓
如果请求 B 是没有 token 的（比如某个白名单接口、或者代码里直接调用了业务方法）
  → BaseContext.getCurrentId() 返回 1，串号！
```

而且 `ThreadLocalMap` 的 key 是**弱引用**，value 是强引用。线程不销毁，value 就一直挂着 → **内存泄漏**。

**`afterCompletion` 的"completion"是什么意思？** 它在这个请求**完全处理完**之后执行，包括 Controller 抛异常的情况（`ex` 参数就是那个异常）。所以异常路径也能清理干净。

---

## ④ SQL 实况（真实抓取）

### 证据 1：无 token 时，一条 SQL 都没有

```bash
curl -s -o /dev/null -w "HTTP %{http_code}\n" http://localhost:8080/admin/employee/page
```

输出：
```
HTTP 401
```

抓到的 SQL：**空**。

这条证据非常重要，它直接证明：**拦截器在数据库之前就把请求挡死了**。请求根本没走到 `EmployeeServiceImpl.pageQuery`，所以没有 `SELECT COUNT(*)`，没有 `LIMIT`。

对比一下"如果拦截器失效"会怎样：那就是任何人不带 token 都能拉到全部员工列表（含密码哈希）。**拦截器不是"辅助功能"，它是唯一的安全边界。**

### 证据 2：`create_user = 1` 是 ThreadLocal 链路的活证据

看新增员工时抓到的 SQL：

```sql
INSERT INTO employee ( name, username, password, phone, sex, id_number, status,
                       create_time, update_time, create_user, update_user )
VALUES ( '测试员', 'test_precision', 'e10adc3949ba59abbe56e057f20f883e',
         '13900000001', '1', '110101199001011234', 1,
         '2026-10-27 22:42:21.177684', '2026-10-27 22:42:21.177684', 1, 1 )
```

注意最后的 `1, 1`——那是 `create_user` 和 `update_user`。

这两个 `1` 是从哪来的？`EmployeeServiceImpl.save` 里**根本没有设置它**：

```java
public void save(EmployeeDTO employeeDTO) {
    Employee employee = new Employee();
    BeanUtils.copyProperties(employeeDTO, employee);
    employee.setStatus(StatusConstant.ENABLE);
    employee.setPassword(...);
    employeeMapper.insert(employee);   // ← 没有 setCreateUser
}
```

它是这么来的：

```
HTTP 请求头 token: eyJ... （载荷 empId=1）
  → 拦截器 parseJwt 解出 1
  → BaseContext.setCurrentId(1L)          ← 存进 ThreadLocal
  → Service 里调 employeeMapper.insert()
  → MyBatis-Plus 回调 MyMetaObjectHandler.insertFill()
  → handler 调 BaseContext.getCurrentId() → 1
  → SQL 里 create_user = 1
```

**这条链是完整的、可验证的。** 面试时如果你能画出这条链并指出 SQL 里那个 `1` 的来源，比背十句"ThreadLocal 用于传参"都有说服力。

### 证据 3：拦截器 0 次查库

上面两次正常请求（登录、新增员工）抓到的 SQL 里，**没有任何一条是拦截器产生的**。登录只有 1 条 `SELECT`（查员工），新增只有 1 条 `INSERT`。

这印证了 JWT 的核心优势：**验签是纯 CPU 运算，不需要查库、不需要查 Redis。**

---

## ⑤ 权衡

### 拦截器 vs 过滤器 vs AOP，为什么选拦截器？

| | Filter（`javax/jakarta.servlet.Filter`） | **Interceptor（当前方案）** | AOP 切面 |
|---|---|---|---|
| 所在层 | Servlet 容器层 | **Spring MVC 层** | Spring Bean 层 |
| 能不能拿到 `handler`（知道要调哪个方法） | **不能** | **能** | 能 |
| 能不能拿到方法上的注解 | 不能 | **能**（`HandlerMethod`） | 能 |
| 能不能拿到 Bean（`@Autowired`） | 不能（不在 Spring 容器管理链上） | **能** | 能 |
| 适合做什么 | 编码、跨域、日志埋点 | **鉴权、权限** | 事务、缓存、埋点 |

**这个项目选拦截器的决定性原因**：需要 `@Autowired` 注入 `JwtProperties`。Filter 默认不是 Spring Bean，注入为 null（虽然可以用 `@Component` 注册 Filter 让它进容器，但那是绕路）。而且拦截器能拿到 `HandlerMethod`，将来要做"某个接口需要 ADMIN 角色"这种细粒度权限，直接读方法上的注解就行。

### ThreadLocal vs 每层加参数，为什么选 ThreadLocal？

对比一下 `empId` 传递的两条路：

```java
// 方案 A：参数传递（不用 ThreadLocal）
public Result<Void> save(@RequestBody EmployeeDTO dto) {
    employeeService.save(dto, BaseContext.getCurrentId());   // Controller 传
}
public void save(EmployeeDTO dto, Long operatorId) {          // Service 收
    employeeMapper.insert(employee, operatorId);              // 再传
}
```

问题：**每加一个需要"当前用户"的方法，就要多一个参数**。而且有些地方根本没法加参数——比如 `MyMetaObjectHandler.insertFill(MetaObject)`，这是框架回调，**方法签名是 MyBatis-Plus 定死的，你改不了**。

```java
// 方案 B：ThreadLocal（当前）
public void save(EmployeeDTO dto) {
    employeeMapper.insert(employee);   // 谁都不传，handler 自己从 ThreadLocal 取
}
```

**这就是 ThreadLocal 不可替代的场景：跨越框架回调传值。** 你没法给框架的方法加参数，只能靠"当前线程的隐式上下文"。

同一类场景还有：AOP 切面里、日志 MDC、事务同步器、`SecurityContextHolder`（Spring Security 就是这么干的）。

### 一句话对比

> ThreadLocal 不是"传参的语法糖"，它是**在无法传参的调用链里传递上下文**的唯一手段。代价是隐式依赖 + 必须手动清理。

---

## ⑥ 踩坑实录

### 坑 1：`(Long) empId` 直接 `ClassCastException`

前面提过了，但值得展开，因为它的表现极具迷惑性：

- **登录接口正常**（登录不走拦截器）
- **一调需要鉴权的接口就 500**
- 错误在日志里被 `GlobalExceptionHandler` 兜成"未知错误"，前端只看到 `code:0`

第一反应会怀疑"token 有问题"，但 token 明明刚发的。真凶是 Jackson 把小整数反序列化成了 `Integer`。

```java
// ✗ 错
Long empId = (Long) claims.get("empId");
// ✓ 对
long empId = ((Number) claims.get("empId")).longValue();
```

### 坑 2：一开始把拦截器加到了 `/**`

最早的版本写的是：

```java
registry.addInterceptor(jwtTokenAdminInterceptor).addPathPatterns("/**")
        .excludePathPatterns("/admin/employee/login");
```

结果前端页面 `http://localhost:8080/` 打不开——因为 `/` 也被拦了，没 token 直接 401。

后来改成只拦 `/admin/**`，并在 `preHandle` 里加 `handler instanceof HandlerMethod` 判断，双保险。

### 坑 3：`afterCompletion` 忘记清，测试时"看起来没问题"

不清 ThreadLocal，**单个用户测试永远测不出来**——因为每次请求你都是同一个 empId，串号了也看不出来。

只有用两个账号交替请求、或者用 `@Async` 异步任务，才会暴露。这类 bug 在生产上是"某用户的订单显示成别人的"级别的事故。

**这也解释了为什么这类 bug 只能靠代码审查发现，不能靠测试发现。** 所以 `afterCompletion` 那三行，比 `preHandle` 里那十几行更重要。

### 坑 4：日志中文乱码 `ÏµÍ³Òì³£`

应用跑在 Windows CMD 里，`log.error("系统异常", ex)` 输出的中文是乱码。原因是 CMD 默认 GBK（代码页 936），JVM 的 `file.encoding` 和 `sun.stdout.encoding` 没对齐。

不影响功能，但**会让你看不出错误信息**。临时解决：`chcp 65001` 切 UTF-8，或者用 IDEA 的控制台看（IDEA 默认 UTF-8）。

---

## ⑦ 缺口

| 缺口 | 严重度 | 说明 |
|---|---|---|
| **只验签名，不查库** | 中 | token 签发后员工被禁用/删除/改密码，token 在 TTL 内依然有效 |
| **没有角色/权限区分** | 中 | 只要能过签名，所有 `/admin/**` 接口都能调。真实系统需要"店长能看报表、服务员只能接单"的权限模型 |
| **`@Async` / 线程池里取不到用户** | 中 | ThreadLocal 只属于创建它的线程。异步任务里 `BaseContext.getCurrentId()` 返回 null |
| **WebSocket 线程没有上下文** | 低 | `@ServerEndpoint` 的回调跑在容器 IO 线程，取不到 HTTP 请求的 ThreadLocal |
| **跨域预检会 401** | 低（当前架构无影响） | 前后端同源部署（静态资源打进 jar）所以不触发。若前后端分离，`OPTIONS` 预检不带 token 会被拦，需要放行 |
| **`afterCompletion` 里没有异常兜底** | 低 | 如果 `remove()` 之前有代码抛异常（目前没有），清理会跳过 |

### 「`@Async` 取不到用户」这个坑值得单说

如果以后加了异步导出（比如"生成大报表后邮件发送"）：

```java
@Async
public void exportAsync() {
    Long id = BaseContext.getCurrentId();   // null！
}
```

因为 `@Async` 把任务交给了另一个线程，而 ThreadLocal 是**线程私有**的。

**解法**：把 id 作为参数传进去；或者用 `TaskDecorator` 把父线程的上下文复制到子线程；或者用 `TransmittableThreadLocal`（阿里开源的 TTL）。

---

## ⑧ 面试 30 秒版

> 管理端所有 `/admin/**` 请求都会过 `JwtTokenAdminInterceptor`。它先判断 handler 是不是 `HandlerMethod`（避免拦到静态资源），再从请求头 `token` 取票，用 HMAC-SHA256 验签，解出 `empId` 存进 `BaseContext`（ThreadLocal），最后 `afterCompletion` 里必须 `remove`——因为 Tomcat 线程池复用线程，不清会把上一个用户的 id 带给下一个请求，而且会造成内存泄漏。
>
> 拦截器全程 0 次查库，验签是纯 CPU 运算，这是 JWT 相对 session 的核心优势。但代价是无法主动吊销：员工被禁用后，他手上的 token 在 TTL 内还能用，要靠短 TTL + 黑名单或版本号解决。
>
> 有一个细节：从 claims 取 id 必须用 `((Number) v).longValue()`，不能直接 `(Long)` 强转——小整数反序列化后是 `Integer`，强转会 `ClassCastException`。
