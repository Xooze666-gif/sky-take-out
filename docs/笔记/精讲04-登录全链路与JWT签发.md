# 精讲 04 · 登录全链路与 JWT 签发

> 源码位置：`EmployeeController.login` → `EmployeeServiceImpl.login` → `JwtUtil.createJwt`
> 八层拆解：① 场景 ② 数据 ③ 逐行 ④ SQL 实况 ⑤ 权衡 ⑥ 踩坑 ⑦ 缺口 ⑧ 面试 30 秒版

---

## ① 场景

管理员打开登录页，输入 `admin / 123456`，点登录。前端发出：

```
POST /admin/employee/login
Content-Type: application/json

{"username":"admin","password":"123456"}
```

这个接口是**唯一不需要 token 的接口**（在 `WebMvcConfiguration` 里被 `excludePathPatterns` 排除了）。它要交付的东西只有一件：**一个能被后续所有接口信任的 token**。

---

## ② 数据：执行前 → 执行后

**执行前**，`employee` 表里已经有一行（密码是 MD5，不是明文）：

| id | name | username | password | status |
|---|---|---|---|---|
| 1 | 管理员 | admin | `e10adc3949ba59abbe56e057f20f883e` | 1 |

`e10adc3949ba59abbe56e057f20f883e` 就是 `123456` 的 MD5 —— 这个值你可以在任何 MD5 工具里验证。

**执行后**，数据库**一个字节都没变**。变的只有响应体：

```json
{"code":1,"msg":null,"data":{"id":1,"userName":"admin","name":"管理员","token":"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."}}
```

**注意：登录是纯读操作。** 这是第一个要建立的概念——登录不改库，它只是"核对身份 + 发一张票"。

---

## ③ 逐行拆

### 第 1 步：空的挡在最前面

```java
if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
    throw new AccountNotFoundException("用户名或密码为空");
}
```

`StringUtils.hasText` 同时挡 `null`、`""`、`"   "`（全空格）。如果只用 `username == null`，前端传个空格字符串就会带着 `WHERE username = '   '` 去查库，白跑一次 SQL。

**为什么这个消息用的是 `AccountNotFoundException`？** 因为项目里没为"参数为空"单独建异常类，复用了最接近的。这不算优雅，但比抛 `RuntimeException` 好——至少 `GlobalExceptionHandler` 能把它转成 `{code:0,msg:...}`。

### 第 2 步：查人

```java
Employee employee = employeeMapper.selectOne(
        Wrappers.<Employee>lambdaQuery().eq(Employee::getUsername, username));
```

`selectOne` 的语义是"必须最多一条"。如果 `username` 上有唯一索引（项目里有，`idx_username`），这里不可能查出多条；如果没有唯一索引且有两条重名数据，MyBatis-Plus 会抛 `TooManyResultsException`。**所以 `selectOne` 用在没唯一约束的列上是危险的**——这也是为什么建表时给 `username` 加了唯一索引。

### 第 3 步：比对密码（关键——在 Java 里比，不在数据库里比）

```java
String md5 = DigestUtils.md5DigestAsHex(password.getBytes(StandardCharsets.UTF_8));
if (!md5.equals(employee.getPassword())) {
    throw new PasswordErrorException();
}
```

这里有个很多人第一反应会写错的地方：**没有 `WHERE password = ?`**。

为什么？因为密码是**不可逆哈希**，永远不该作为查询条件传给数据库（会进慢查询日志、general_log、被 DBA 看到）。正确做法就是像这样：**按用户名查出整行，把哈希取回 Java 内存再比。**

还有一个细节：`password.getBytes(StandardCharsets.UTF_8)` 显式指定了字符集。不指定的话用的是平台默认编码，Windows 默认 GBK、Linux 默认 UTF-8，同一串中文密码在不同机器上算出的 MD5 会不一样——**这是跨平台部署时的经典坑**。

### 第 4 步：状态检查（放在密码之后）

```java
if (StatusConstant.DISABLE.equals(employee.getStatus())) {
    throw new AccountLockedException();
}
```

**为什么状态检查在密码之后？** 如果先检查状态，攻击者随便输个错密码，只要账号被禁用就会收到"账号被锁定"——等于免费确认了"这个账号存在且被禁用"。放在密码之后再查，攻击者得先知道正确密码才能看到这条提示。

这个顺序是刻意设计的，面试被问"你登录的校验顺序为什么是这样"就是得分点。

### 第 5 步：发 token

```java
// Controller
Map<String, Object> claims = new HashMap<>();
claims.put(JwtClaimsConstant.EMP_ID, employee.getId());   // 只放 id
String token = JwtUtil.createJwt(jwtProperties.getAdminSecretKey(), jwtProperties.getAdminTtl(), claims);
```

`createJwt` 内部干了三件事：

```java
payload.put("iat", now / 1000);              // 签发时间（秒）
payload.put("exp", (now + ttlMillis) / 1000); // 过期时间（秒）
String content = HEADER_PART + "." + payloadPart;
return content + "." + encode(sign(secretKey, content));
```

**注意 `iat`/`exp` 存的是「秒」不是「毫秒」**。JWT 规范（RFC 7519）规定这两个字段是 NumericDate = 从 epoch 起的**秒数**。这里除以 1000 是必须的——如果存毫秒，`exp` 会变成一个巨大的数，`parseJwt` 里 `now/1000 > expSeconds` 永远不成立，token **永远不过期**。这是一个不发散、但极其致命的 bug，很难在测试中发现（因为"能用"）。

### 第 6 步：为什么要 `LinkedHashMap`

```java
Map<String, Object> payload = new LinkedHashMap<>();
```

如果是普通 `HashMap`，`claims` 里的字段和 `iat`/`exp` 的**输出顺序不确定**。虽然 JWT 不要求字段有序，但你会遇到一个很烦的问题：**同样的登录请求，两次生成的 token 字符串不一样**（因为 payload 序列化顺序变了）。这会让你在排查问题时误以为"token 每次都变，是不是有 bug"。

用 `LinkedHashMap` 保证顺序稳定，token 在一定时间窗内是**确定性可复现**的，调试友好。

---

## ④ SQL 实况（真实抓取）

用 `general_log` 抓了三种登录情况，结果非常干净：

**① 正确密码登录**
```sql
SELECT id,name,username,password,phone,sex,id_number,status,create_time,update_time,create_user,update_user
FROM employee WHERE (username = 'admin')
```
→ 1 条 SQL，返回 `{"code":1,...,"token":"eyJ..."}`

**② 密码错误**
```sql
SELECT id,name,username,password,... FROM employee WHERE (username = 'admin')
```
→ 1 条 SQL，返回 `{"code":0,"msg":"密码错误"}`

**③ 用户不存在**
```sql
SELECT id,name,username,password,... FROM employee WHERE (username = 'nobody')
```
→ 1 条 SQL，返回 `{"code":0,"msg":"账号不存在"}`

### 这三条 SQL 说明了什么（这是本篇最值钱的部分）

**三种情况的 SQL 数量完全一样（都是 1 条），且没有任何 UPDATE。**

这意味着：
1. **密码比对完全发生在应用层**，数据库根本没参与"对不对"的判断。你从数据库层面永远看不出"刚才有人密码试错了"。
2. `SELECT` 把 `password` 列**一起查出来了**。这是必要的（要比对），但也意味着**只要日志级别够细，密码哈希会出现在日志里**。生产上应该把 `log-impl` 关掉（项目里 `application.yml` 现在开着 `StdOutImpl`，是给开发自查用的）。
3. **登录失败不会写任何审计记录**。真实系统一般会有一条 `login_log` 表（谁、什么时间、IP、成功失败），这个项目没有——这是缺口，后面第 ⑦ 层讲。

---

## ⑤ 权衡：几个"为什么不用另一种写法"

### 为什么密码用 MD5 而不是 BCrypt？

诚实地说：**MD5 是错的，BCrypt 才是对的。**

| | MD5（当前） | BCrypt（应该用） |
|---|---|---|
| 加盐 | 无（相同密码 → 相同哈希） | 自动随机盐，每条不同 |
| 计算速度 | 极快（GPU 每秒几十亿次） | 刻意慢（可调 cost，默认约 100ms/次） |
| 抗彩虹表 | 弱（`123456` 的 MD5 网上直接能搜到） | 强（盐随机，彩虹表失效） |
| 抗暴力破解 | 弱 | 强（每次校验 100ms，爆破成本 ×百万） |

你现在可以自己验证前者有多弱：`123456` 的 MD5 = `e10adc3949ba59abbe56e057f20f883e`，这个值在任何一个 MD5 反查网站直接出明文。

**面试怎么处理这个点？** 不要说"MD5 够用"，要说：

> 我项目里用的是 MD5，因为要跟课程/演示数据对齐。但我知道这是不安全的：MD5 无盐、速度快，`123456` 的哈希网上直接能反查。生产环境我会用 `BCryptPasswordEncoder`，它自带随机盐、计算慢，天然抗彩虹表和暴力破解。切换成本很低，只改 `EmployeeServiceImpl` 里那两行，存密码的地方和比密码的地方一起改。

**主动指出自己的技术债，比被面试官问出来强十倍。** 这句话说出口，面试官对你的评价是"知道边界"，而不是"不懂安全"。

### 为什么 token 只放 `empId`，不放名字/手机号？

三层原因，从浅到深：

1. **token 的 payload 是 Base64 编码，不是加密**。任何人都能解开看。你把手机号放进去，等于把手机号发给了每一个能拿到 token 的人（浏览器 localStorage、抓包工具、日志）。
2. **信息会过期**。员工改了名字，旧 token 里还是旧名字。要么等 token 过期，要么做"改名字就强制踢下线"——两个都很麻烦。只放 id，名字每次查库拿最新的。
3. **token 越短越好**。每个请求都要带它，网络开销 × 请求数。

一句话：**token 只放「不会变、不敏感、用来查其他信息的主键」。**

### 为什么管理端和用户端用两套密钥？

`application.yml` 里是这样的：

```yaml
sky:
  jwt:
    admin-secret-key: sky-take-out-admin-secret-key-2026
    admin-token-name: token
    user-secret-key: sky-take-out-user-secret-key-2026
    user-token-name: authentication
```

两套密钥 + 两个请求头，带来三个实际好处：

1. **隔离爆炸半径**：万一用户端密钥泄露（用户端代码在客户端，更容易被逆向），攻击者拿它签的 token 在管理端验不过，进不来后台。
2. **载荷字段不冲突**：管理端载荷用 `empId`，用户端用 `userId`。同一个 key 名在两端语义不同，混用会出诡异 bug。
3. **过期时间可以不同**：管理端 2 小时（后台有人在，短一点安全），用户端 7 天（用户不想天天登录）。

**如果只用一套密钥会怎样？** 用户端 token 拿到管理端也能过签名验证——只要载荷里恰好有对应字段，就能越权进后台。这是很典型的设计事故。

---

## ⑥ 踩坑实录

### 坑 1：我一开始以为"密码错误"是数据库判的

调试时我想验证"密码错"和"账号不存在"的区别，第一反应是去看 SQL 的 `WHERE` 条件，结果发现**两次 SQL 一模一样**（都是 `WHERE username = 'admin'`），当时还以为代码写漏了。

真相是：数据库只负责"把人找出来"，对错由 Java 比。这个认知一旦建立，后面看很多登录相关的问题就通了——比如"为什么改密码不需要动登录 SQL"。

### 坑 2：`GET /admin/employee/info` 返回的不是对象，是一个数字

做前端管理页的时候，我在 `init()` 里写：

```js
fetch(API + '/admin/employee/info', { headers: { token } })
  .then(r => r.json())
  .then(data => { currentUser = data.data; enterApp(); });
```

然后页面上 `currentUser.name` 一直是 `undefined`。

看后端才发现：

```java
@GetMapping("/info")
public Result<Long> info() {
    return Result.success(BaseContext.getCurrentId());   // 返回的是 Long，不是 Employee
}
```

接口契约是 `Result<Long>`，返回的就是 `1` 这个数字。前端把数字当对象取 `.name`，当然是 `undefined`。

**修法**：`init()` 改成只用它做"token 是否还有效"的校验，姓名从前端登录时缓存的 `localStorage` 里取。

这个坑的教训是：**接口返回值类型必须跟前端对齐，`Result<Long>` 和 `Result<Employee>` 差一个泛型，前端就是白屏。**

### 坑 3：curl 测登录时中文参数 400

```bash
curl "http://localhost:8080/admin/employee/page?name=张"
```
→ `400 Bad Request: Invalid character found in the request target`

不是代码问题，是 curl 没做 URL 编码。正确姿势：

```bash
curl -G "http://localhost:8080/admin/employee/page" --data-urlencode "name=张"
```

**这个坑值得记，因为真实前端 `fetch` 会自动编码，只有手写 URL 测试才会踩。**（我这次抓分页 SQL 时又踩了一次，第一次返回的是 Tomcat 的 400 页面，不是 JSON。）

---

## ⑦ 缺口：这段代码的问题（真懂才说得出）

| 缺口 | 严重度 | 说明 | 怎么改 |
|---|---|---|---|
| **MD5 无盐** | 高 | 见 ⑤ | 换 BCrypt |
| **错误提示泄露账号存在性** | 中 | "账号不存在"/"密码错误"/"账号被锁定" 三种不同提示，攻击者能批量探测哪些用户名有效 | 统一成"用户名或密码错误"；锁定状态也不单独提示 |
| **没有登录失败次数限制** | 中 | 可以无限次爆破，多少次都行 | Redis 计数：同一账号/IP 5 次失败锁 15 分钟 |
| **token 无法主动失效** | 中 | 员工被禁用后，他手上那个 token 在 2 小时内**依然能用**（因为拦截器只验签名，不查库） | 拦截器里加一次 status 查询，或用 Redis 存"已失效 token"黑名单 |
| **密钥硬编码在 yml** | 高（生产） | `sky-take-out-admin-secret-key-2026` 明文在配置文件里，代码库泄露 = 密钥泄露 | 环境变量 / 配置中心 |
| **无登录审计日志** | 低 | 谁在什么时候登录失败，查不到 | 加 `login_log` 表 |

### 其中「token 无法主动失效」值得单独说

这是 JWT 的**固有代价**，不是这个项目写错了。JWT 的设计就是"无状态"——服务端不存东西，验签就完事。好处是不用查库、天然支持水平扩展；代价就是**发出去的票收不回来**。

你现在禁用员工，他手上的 token 还能用满 2 小时。真实业务里通常这样解：

- **短 TTL + Refresh Token**：access token 只活 15 分钟，refresh token 存库（可吊销）
- **黑名单**：登出/禁用时把 token 的 `jti` 塞进 Redis，拦截器查一次
- **版本号**：用户表加 `token_version`，签发时写进载荷，拦截器比对，改密码/禁用就 +1

**面试如果被问"JWT 怎么实现登出"，答"JWT 本身不支持登出，要靠黑名单或版本号"，这就是标准答案。** 答"删掉前端的 token"会被认为不理解服务端。

---

## ⑧ 面试 30 秒版

> 登录接口是唯一免鉴权的。流程是：先挡空参数，按用户名查一次库把整行捞出来，密码在 Java 侧做 MD5 后跟库里哈希比对——**密码绝不作为 SQL 查询条件**。状态检查放在密码之后，避免通过"账号被锁定"这个提示免费确认账号存在。验证通过后签 JWT：载荷只放 `empId`，因为 payload 只是 Base64 不是加密，敏感信息不能进；`iat`/`exp` 按规范存**秒**不存毫秒。管理端和用户端用两套密钥、两个请求头，这样一端泄露不会波及另一端。
>
> 已知短板：MD5 无盐，生产要换 BCrypt；JWT 无状态导致 token 无法主动吊销，被禁用的员工手上那张票还能活满 2 小时，要配短 TTL + 黑名单或版本号来解决。

---

## 附：怎么自己复现

```bash
# 开日志
"E:/env/mysql-8.0/bin/mysql.exe" -uroot -p123456 -e "SET GLOBAL log_output='TABLE'; SET GLOBAL general_log=ON;"

# 打请求
curl -s -X POST http://localhost:8080/admin/employee/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"123456"}'

# 看 SQL
"E:/env/mysql-8.0/bin/mysql.exe" -uroot -p123456 --default-character-set=utf8mb4 -e \
"SELECT argument FROM mysql.general_log WHERE command_type='Query' AND argument NOT LIKE '%general_log%' ORDER BY event_time"

# 关日志（重要，别留坑）
"E:/env/mysql-8.0/bin/mysql.exe" -uroot -p123456 -e "SET GLOBAL general_log=OFF; TRUNCATE TABLE mysql.general_log;"
```
