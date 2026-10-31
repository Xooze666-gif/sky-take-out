# 精讲 10 · 统计报表与 Excel 导出

> 源码位置：`ReportController` → `ReportServiceImpl` → `ReportMapper.xml`
> 八层拆解：① 场景 ② 数据 ③ 逐行 ④ SQL 实况 ⑤ 权衡 ⑥ 踩坑 ⑦ 缺口 ⑧ 面试 30 秒版

---

## ① 场景

管理端「数据统计」页，四个接口 + 一个导出：

| 接口 | 作用 | 返回 |
|---|---|---|
| `GET /admin/report/turnoverStatistics?begin=&end=` | 营业额 | 每天：营业额、有效订单数、完成率、客单价 |
| `GET /admin/report/userStatistics?begin=&end=` | 用户 | 每天：新增用户、累计用户 |
| `GET /admin/report/orderStatistics?begin=&end=` | 订单 | 每天：订单总数、有效订单、完成率 |
| `GET /admin/report/salesTop10?begin=&end=` | 销量排行 | 前 10 名：商品名、销量 |
| `GET /admin/report/export` | 导出 | `.xlsx` 文件流 |

**关键：这四个接口的返回值不是"数据库里的原始数据"，而是"聚合 + 计算后的业务指标"。** 这个"计算"发生在哪一层，是理解这段代码的核心。

---

## ② 数据：口径与真实数字

假设 `orders` 表的真实数据是：

| id | order_time | status | amount |
|---|---|---|---|
| 1 | 2026-10-17 10:00 | 5 已完成 | 88.00 |
| 2 | 2026-10-17 12:00 | 2 待接单 | 78.00 |
| 3 | 2026-10-18 13:00 | 5 已完成 | 156.00 |
| 4 | 2026-10-18 14:00 | 5 已完成 | 38.00 |

**接口返回（真实抓取）：**

```json
[
  {"date":"2026-10-17","turnover":88.0,  "validOrderCount":1,"orderCompletionRate":0.5,"unitPrice":88.0,"newUsers":null},
  {"date":"2026-10-18","turnover":194.0, "validOrderCount":2,"orderCompletionRate":1.0,"unitPrice":97.0,"newUsers":null}
]
```

**逐项核对：**

| 日期 | 营业额 | 怎么算的 | 有效订单 | 完成率 | 客单价 |
|---|---|---|---|---|---|
| 10-17 | 88.0 | 只有订单 1 是 status=5，**订单 2 的 78 被排除** | 1 | 1/2 = 0.5 | 88/1 = 88 |
| 10-18 | 194.0 | 156 + 38 = 194，两笔都是 status=5 | 2 | 2/2 = 1.0 | 194/2 = 97 |

**两个关键口径：**

1. **营业额只算 `status = 5`（已完成）**。待接单的 78 元不算——因为还没真正成交，可能被拒单退款。
2. **完成率 = 有效订单 / 总订单**。10-17 有 2 单，只有 1 单完成，所以 0.5。

**注意 `newUsers` 是 `null`。** `BusinessDataVO` 这个类里有个 `newUsers` 字段，但营业额统计根本不填它 —— 这是个设计瑕疵，详见第 ⑦ 层。

---

## ③ 逐行拆

### 营业额 SQL：口径全部体现在 `WHERE` 里

```sql
select date_format(order_time, '%Y-%m-%d')  as date,
       round(sum(amount), 2)                as turnover,
       count(*)                             as validOrderCount
from orders
where status = 5                                          -- ① 口径
  and order_time >= #{begin}
  and order_time < date_add(#{end}, interval 1 day)       -- ② 边界
group by date_format(order_time, '%Y-%m-%d')
order by date
```

**① `status = 5` 是业务口径，不是技术细节。**

这是整个报表模块最重要的一行。如果漏了它，待接单、已取消、退款中的订单都会被算进营业额 —— 报表数字虚高，老板看到的是"赚了很多钱"，实际现金流对不上。

**面试被问"你的营业额怎么统计的"，第一句就要说 `status=5`。**

**② `date_add(#{end}, interval 1 day)` 是日期区间查询的标准写法。**

为什么不能写 `order_time <= #{end}`？

```
end 传的是 "2026-10-18"（Java 的 LocalDate）
MyBatis 把它传给 MySQL，MySQL 会当成 "2026-10-18 00:00:00"

order_time <= '2026-10-18 00:00:00'
  → 只能匹配到 10-18 零点整那一刻的订单
  → 10-18 全天的订单（13:00、14:00）全部漏掉！
```

**正确做法：把 `end` 加一天，然后用 `<`。**

```
order_time < '2026-10-19 00:00:00'
  → 匹配 10-18 全天（到 23:59:59.999）
  → 不包含 10-19 零点
```

**为什么不用 `<= date_add(end, interval 1 day)`？** 那样会把 `2026-10-19 00:00:00` 这一瞬间的订单也算进来。虽然概率极低，但严格来说错了。

**这个坑极其高频。** 只要做日期区间查询就会遇到，而且测试时如果数据都在中午，你根本发现不了——因为"漏掉当天"的表现是"少了几条"，不是"报错"。

### `date_format` vs `date()`

营业额和订单统计用的是 `date_format(order_time, '%Y-%m-%d')`，用户统计用的是 `date(create_time)`。

**两者结果一样（都返回 `2026-10-17`），但有区别：**

| | `date_format(...)` | `date(...)` |
|---|---|---|
| 返回类型 | **字符串** `VARCHAR` | **日期** `DATE` |
| Java 映射 | `String` | `LocalDate` |
| 能否直接比较 | 字符串比较（`'2026-10-17' < '2026-10-18'` 成立） | 日期比较 |
| 性能 | 有格式化开销 | 更快 |

**项目里的实际后果（这是个真 bug 温床）：**

- `BusinessDataVO.date` 是 `String`
- `UserReportVO.date` 是 `LocalDate`

所以 `ReportServiceImpl` 里要这样转：

```java
// 营业额统计里，用 UserReportVO 的 LocalDate 拼 Map 的 key
totalOrderMap.put(orderReport.getDate().toString(), orderReport.getTotalOrderCount());
// 再用 String 的 date 去查
Integer totalCount = totalOrderMap.getOrDefault(vo.getDate(), 0);
```

**一边是 `LocalDate`，一边是 `String`，中间靠 `.toString()` 桥接。** 这种"同一份数据两种类型"的不一致，是前面 SQL 写法不统一导致的。**统一用 `date()` 会更好。**

### 完成率和客单价：为什么不在 SQL 里算？

`turnoverStatistics` 的 SQL 只返回了 `turnover`（营业额）和 `validOrderCount`（有效订单数）。**完成率和客单价是 Service 层算的：**

```java
public List<BusinessDataVO> turnoverStatistics(LocalDate begin, LocalDate end) {
    List<BusinessDataVO> list = reportMapper.turnoverStatistics(begin, end);

    // ① 先拿一张"每天总订单数"的 Map
    Map<String, Integer> totalOrderMap = new HashMap<>();
    for (OrderReportVO orderReport : reportMapper.orderStatistics(begin, end)) {
        totalOrderMap.put(orderReport.getDate().toString(), orderReport.getTotalOrderCount());
    }

    for (BusinessDataVO vo : list) {
        Integer validCount = vo.getValidOrderCount() == null ? 0 : vo.getValidOrderCount();
        Integer totalCount = totalOrderMap.getOrDefault(vo.getDate(), 0);

        // ② 完成率 = 有效 / 总数
        if (totalCount != null && totalCount > 0) {
            vo.setOrderCompletionRate(round(validCount * 1.0 / totalCount));
        } else {
            vo.setOrderCompletionRate(0.0);
        }

        // ③ 客单价 = 营业额 / 有效单数
        Double turnover = vo.getTurnover() == null ? 0.0 : vo.getTurnover();
        if (validCount > 0) {
            vo.setUnitPrice(round(turnover / validCount));
        } else {
            vo.setUnitPrice(0.0);
        }
    }
    return list;
}
```

**三个必须处理的边界：**

**（a）除零。** `totalCount > 0` 和 `validCount > 0` 的判断不是防御性编程，是必须的——某天可能只有"待接单"的订单（`validCount = 0`），也可能一单都没有（`totalCount = 0`）。不判就是 `ArithmeticException: / by zero`（`double` 除法不会抛，但会得到 `NaN` 或 `Infinity`，序列化成 JSON 会变成 `"NaN"` 字符串，前端渲染直接崩）。

**（b）`validCount * 1.0` 的 `1.0` 不能省。** 两个 `Integer` 相除是**整数除法**：`1 / 2 = 0`。乘 `1.0` 强制转成 `double`。

**这是 Java 里最容易犯的错之一**，而且不报错——你只会看到"完成率永远是 0 或 1"。

**（c）`round()` 只保留两位。**

```java
private double round(double value) {
    return Math.round(value * 100) / 100.0;
}
```

`Math.round(x * 100)` 返回 `long`，再 `/ 100.0` 转回 `double`。注意是 `/ 100.0` 不是 `/ 100` —— 后者又是整数除法。

**为什么不用 `BigDecimal.setScale(2, RoundingMode.HALF_UP)`？** 金额计算确实应该用 `BigDecimal`。但这里是**统计展示**（不是结算），`double` 的精度误差（约 1e-16 量级）在这个场景可以接受。**如果这个数字要参与下一步的金额计算，就必须换 `BigDecimal`。**

### 用户统计：累计数怎么来的

```java
public List<UserReportVO> userStatistics(LocalDate begin, LocalDate end) {
    List<UserReportVO> list = reportMapper.userStatistics(begin, end);

    // ① 先查"区间开始之前"的累计基数
    int cumulative = reportMapper.countUsersByDate(begin.minusDays(1));

    // ② 逐日累加
    for (UserReportVO vo : list) {
        cumulative += vo.getNewUserCount();
        vo.setTotalUserCount(cumulative);
    }
    return list;
}
```

对应的 SQL：

```sql
-- ① 区间内每天新增
select date(create_time) as date, count(*) as newUserCount
from user
where create_time >= #{begin} and create_time < date_add(#{end}, interval 1 day)
group by date(create_time) order by date

-- ② 区间之前的累计
select count(*) from user where create_time < date_add(#{end}, interval 1 day)
```

**注意 ② 传的参数**：`countUsersByDate(begin.minusDays(1))`，即 `begin` 前一天。SQL 里又 `date_add(#{end}, interval 1 day)` —— 所以实际条件是：

```
create_time < date_add('2026-10-16', interval 1 day)
          = create_time < '2026-10-17 00:00:00'
```

意思是"10-17 之前注册的所有用户"。**这就是累计的基数。**

**为什么不直接用 SQL 窗口函数算累计？**

```sql
select date(create_time) as date,
       count(*) as newUserCount,
       sum(count(*)) over (order by date(create_time)) as totalUserCount
from user ...
```

可以，MySQL 8 支持窗口函数。但：

1. **窗口函数的累计是从"区间第一行"开始，不是从"全表"开始**。要得到"绝对累计"，还得加上区间前的基数——问题没消失，只是换了个地方。
2. **可读性**：`sum(count(*)) over (...)` 这种嵌套聚合，很多人第一眼看不懂。
3. **性能**：这个数据量下（几万用户），两次简单查询比一次窗口函数更快（窗口函数要排序 + 物化）。

**"可读性优先，量大再优化"** —— 这是可以说的理由。

### 销量 Top10：为什么要 join `orders`

```sql
select od.name as name, sum(od.number) as number
from order_detail od
         join orders o on od.order_id = o.id
where o.status = 5                                    -- ① 只算已完成订单的明细
  and o.order_time >= #{begin}
  and o.order_time < date_add(#{end}, interval 1 day)
group by od.name
order by number desc
limit 10
```

**`order_detail` 表里没有 `status`、没有 `order_time`，所以必须 join `orders` 才能过滤。**

**① 为什么只算已完成订单？** 因为待接单/已取消的订单里，商品并没有真正卖出。如果把取消的订单也算进销量，会出现"某菜品销量第一但一单没成"的荒谬结果。

**`group by od.name` 而不是 `group by od.dish_id` —— 这是刻意的。**

因为 `order_detail.name` 是**下单时的名称快照**。如果按 `dish_id` 分组再 join 菜品表拿名字，会遇到"菜品改名了，历史销量显示新名字"的问题。

**但按 `name` 分组也有问题**：如果菜品改名（"麻婆豆腐" → "麻婆豆腐（微辣）"），同一个菜品会被拆成两组。

**两种做法的取舍：**

| | `group by name`（当前） | `group by dish_id` |
|---|---|---|
| 改名后 | 拆成两条（销量分散） | 合并成一条（但显示新名字） |
| 菜品删除后 | 正常显示（name 是快照） | 名字变 null |
| 同名不同菜 | 会被错误合并 | 正确分开 |

**更稳的做法是 `group by dish_id` + 显示时用快照名**：

```sql
select od.dish_id, max(od.name) as name, sum(od.number) as number
from ... group by od.dish_id
```

这个项目里菜品名基本不变，所以 `group by name` 够用。**但面试被问"菜品改名后销量统计会怎样"，能答出这个就是细节控。**

### Excel 导出：POI 生成 xlsx

```java
public void exportBusinessData(HttpServletResponse response) {
    LocalDate end = LocalDate.now();
    LocalDate begin = end.minusDays(29);              // ① 固定最近 30 天
    List<BusinessDataVO> list = turnoverStatistics(begin, end);

    try (Workbook workbook = new XSSFWorkbook()) {    // ② try-with-resources
        Sheet sheet = workbook.createSheet("营业额报表");

        // 列宽（单位是 1/256 个字符宽）
        sheet.setColumnWidth(0, 18 * 256);

        // 表头样式
        CellStyle headStyle = workbook.createCellStyle();
        Font headFont = workbook.createFont();
        headFont.setBold(true);
        headStyle.setFont(headFont);

        String[] headers = {"日期", "营业额", "有效订单数", "订单完成率", "平均客单价"};
        Row headRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headStyle);
        }

        int rowIndex = 1;
        for (BusinessDataVO vo : list) {
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(vo.getDate());
            row.createCell(1).setCellValue(vo.getTurnover() == null ? 0.0 : vo.getTurnover());
            // ...
        }

        // ③ 关键：文件名 + 响应头
        String fileName = "营业额报表_" + begin + "_至_" + end + ".xlsx";
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition",
                "attachment; filename*=UTF-8''" + URLEncoder.encode(fileName, StandardCharsets.UTF_8.name()));

        try (OutputStream out = response.getOutputStream()) {
            workbook.write(out);
            out.flush();
        }
    } catch (IOException e) {
        throw new RuntimeException("Excel 导出失败", e);
    }
}
```

**三个技术点：**

**（a）`XSSFWorkbook` 对应 `.xlsx`，`HSSFWorkbook` 对应 `.xls`。**

`XSSF` 是 OOXML 格式（2007+），一个 sheet 最多 104 万行；`HSSF` 是老格式，最多 65536 行。**别用错。**

**（b）`try-with-resources` 保证 workbook 被关闭。**

`Workbook` 实现了 `Closeable`。不关的话，POI 内部持有的临时文件/内存不会释放。**导出几十次就可能 OOM。**

**（c）中文文件名的正确写法：`filename*=UTF-8''` + `URLEncoder`。**

HTTP 响应头只能放 ASCII。直接写中文会在某些容器里被截断或乱码。

正确姿势是 **RFC 5987** 的 `filename*` 语法：

```
Content-Disposition: attachment; filename*=UTF-8''%E8%90%A5%E4%B8%9A%E9%A2%9D%E6%8A%A5%E8%A1%A8.xlsx
                         ↑               ↑
                    字符集声明      URL 编码后的文件名
```

**注意 `''` 是两个单引号**（分隔 charset 和 language，language 为空）。写成 `'` 一个就错了。

---

## ④ SQL 实况（真实抓取）

### 场景 A：营业额统计 —— 一次请求发了 2 条聚合 SQL

```sql
-- 第 1 条：营业额
select date_format(order_time, '%Y-%m-%d') as date,
       round(sum(amount), 2) as turnover,
       count(*) as validOrderCount
from orders
where status = 5
  and order_time >= '2026-10-17'
  and order_time < date_add('2026-10-27', interval 1 day)
group by date_format(order_time, '%Y-%m-%d')
order by date

-- 第 2 条：订单统计（为了拿总订单数算完成率）
select date_format(order_time, '%Y-%m-%d') as date,
       count(*) as totalOrderCount,
       sum(case when status = 5 then 1 else 0 end) as validOrderCount
from orders
where order_time >= '2026-10-17'
  and order_time < date_add('2026-10-27', interval 1 day)
group by date_format(order_time, '%Y-%m-%d')
order by date
```

**为什么是 2 条？** 因为 `turnoverStatistics` 内部调了 `reportMapper.orderStatistics(begin, end)` 来拿"每天总订单数"。

**这是个可以优化的点：** 两条 SQL 都扫了 `orders` 表同一个区间，完全可以合并成一条：

```sql
select date_format(order_time, '%Y-%m-%d') as date,
       round(sum(case when status = 5 then amount else 0 end), 2) as turnover,
       count(*) as totalOrderCount,
       sum(case when status = 5 then 1 else 0 end) as validOrderCount
from orders
where order_time >= #{begin} and order_time < date_add(#{end}, interval 1 day)
group by date_format(order_time, '%Y-%m-%d')
```

**一条 SQL 出三个指标，扫表一次。** 这是 `sum(case when ...)` 条件聚合的经典用途。

### 场景 B：用户统计 —— 2 条 SQL

```sql
select date(create_time) as date, count(*) as newUserCount
from user
where create_time >= '2026-10-17'
  and create_time < date_add('2026-10-27', interval 1 day)
group by date(create_time) order by date

select count(*) from user where create_time < date_add('2026-10-16', interval 1 day)
```

**注意第二条的参数是 `'2026-10-16'`（begin 前一天）**，印证了 `begin.minusDays(1)` 的调用。

### 场景 C：销量 Top10 —— 1 条 SQL

```sql
select od.name as name, sum(od.number) as number
from order_detail od
         join orders o on od.order_id = o.id
where o.status = 5
  and o.order_time >= '2026-10-17'
  and o.order_time < date_add('2026-10-27', interval 1 day)
group by od.name
order by number desc
limit 10
```

**返回结果：**

```json
[{"name":"人气王套餐","number":2},{"name":"水煮鱼","number":1},{"name":"宫保鸡丁","number":1}]
```

### 一个隐藏的细节：`sum(case when ...)` 是"条件聚合"

```sql
sum(case when status = 5 then 1 else 0 end) as validOrderCount
```

这个模式的意思是"**统计满足条件的行数**"。等价写法：

```sql
count(case when status = 5 then 1 end)       -- case 不写 else 时返回 NULL，count 会忽略 NULL
sum(if(status = 5, 1, 0))                    -- MySQL 特有，if() 函数
```

**为什么不用 `count(if(...))` 或 `count(*) where ...`？**

- `count(*)` 不能带条件
- `count(条件)` 要配合 `NULL`（`count` 不计 NULL）
- `sum(case when ...)` 最通用，跨数据库都能用（Oracle、PG 都支持）

**这是数据统计的"瑞士军刀"，面试常考"一条 SQL 统计多种条件的数量"，答案就是这个。**

---

## ⑤ 权衡

### 为什么这四段 SQL 写在 XML 里，而不是用 BaseMapper / 条件构造器？

回顾一下项目的分层原则（精讲 06 提过）：**单表 CRUD 走 BaseMapper，复杂 SQL 走 XML。**

这四段为什么"复杂"：

| 特性 | 条件构造器能做吗 |
|---|---|
| `date_format(order_time, '%Y-%m-%d')` 作为 `group by` 表达式 | ❌ 只能 `groupBy(列名)`，不支持表达式 |
| `sum(case when ...)` 条件聚合 | ❌ 完全没有对应 API |
| `date_add(#{end}, interval 1 day)` | ❌ 只能传值，不能写函数 |
| `join orders o on ...` 多表 | ⚠️ MP 有 `Wrapper` 联表，但很别扭 |
| `limit 10` | ✅ `last("limit 10")`，但拼字符串不优雅 |

**结论：只要涉及"函数、表达式、条件聚合、多表 join"，就该写 XML。**

**XML 的额外好处**：SQL 是纯文本，可以直接贴到 DBA 那里做执行计划分析；而 Java 里拼出来的 SQL 要跑起来才知道长什么样。

### `order_time` vs `checkout_time`，为什么选前者？

两个时间字段，统计口径不同：

| 字段 | 含义 | 用于营业额统计会怎样 |
|---|---|---|
| `order_time` | 下单时间 | 订单在哪天下单，就算哪天的营业额 |
| `checkout_time` | 支付时间 | 订单在哪天付款，就算哪天的营业额 |

**当前用 `order_time`，注释里写了理由：让所有报表共用同一条时间轴。**

**但严格财务口径应该用 `checkout_time`。**

举例：用户 10-31 23:59 下单，10-31 23:59 付款（正常）。用户 10-31 23:58 下单，11-01 00:01 付款（跨天）。

- 用 `order_time`：这笔算 10-31 的
- 用 `checkout_time`：这笔算 11-01 的

**财务对账时**，银行流水按付款时间，所以 `checkout_time` 更准。

**为什么项目还是用了 `order_time`？**

1. 用户统计（`user.create_time`）和订单统计（`orders.order_time`）都按"发生时间"，营业额跟它们对齐，三个报表的数字能互相印证。
2. `checkout_time` 可能是 `null`（未支付订单），过滤时要额外判空。
3. 这个项目的"支付"是模拟的，`order_time` 和 `checkout_time` 相差几秒，不影响结果。

**面试如果被问"营业额按什么时间统计"，答"我用 order_time 保证三个报表时间轴一致；严格财务口径应该用 checkout_time，因为对账是按付款时间的"** —— 这个回答既说清了现状，又说清了正确做法。

### POI vs EasyExcel

| | **POI（当前）** | EasyExcel（阿里） |
|---|---|---|
| 依赖 | `poi-ooxml` | `easyexcel`（底层也是 POI） |
| 内存 | `XSSFWorkbook` **全量加载到内存** | **SAX 流式**，逐行读写 |
| 10 万行 | 约 300MB+ 内存，可能 OOM | 几十 MB |
| API | 手动 createRow/createCell | 注解 + 一行 `EasyExcel.write(...).doWrite(list)` |
| 样式 | 灵活（能精确控制每个 cell） | 简单（注解配置） |

**这个项目导出 30 天的数据（最多 30 行），用 POI 完全没问题。**

**数据量大时必须换 EasyExcel**，因为 `XSSFWorkbook` 是"先把整个 Excel 在内存里构建好，最后一次性写出"。100 万行的报表直接把 JVM 撑爆。

**补充**：POI 也有 `SXSSFWorkbook`（流式写，只保留滑动窗口内的行在内存），是"不想换框架但数据量大"的折中方案。

---

## ⑥ 踩坑实录

### 坑 1：`javax.servlet` 编译报错 —— Boot 3 是 Jakarta EE

第一版代码写的是：

```java
import javax.servlet.http.HttpServletResponse;
```

编译报错：

```
程序包 javax.servlet.http 不存在
```

**原因：Spring Boot 3 全面迁移到 Jakarta EE 9+**，`javax.servlet.*` 改成了 `jakarta.servlet.*`。

```
Spring Boot 2.x → javax.servlet
Spring Boot 3.x → jakarta.servlet
```

**这不是"改个包名"那么简单** —— Jakarta EE 9 是 Oracle 把 Java EE 捐给 Eclipse 基金会后的重新命名，所有 `javax.*` 规范包都变成了 `jakarta.*`。Boot 3 依赖的是 Servlet 5.0+（Jakarta 命名空间）。

**这个坑在 Boot 3 项目里会反复出现**：`javax.validation` → `jakarta.validation`、`javax.persistence` → `jakarta.persistence`、`javax.annotation` → `jakarta.annotation`。

**判断方法**：看项目用的 Servlet API 是哪个包。如果用 `jakarta.*`，所有从网上抄的 Boot 2 教程代码都要改。

同理，`WebSocketServer` 里的 import 也是 jakarta：

```java
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;
```

### 坑 2：POI 依赖离线解析失败

加完依赖后跑 `mvn -o package`（离线模式），报：

```
The following artifacts could not be resolved: org.apache.poi:poi-ooxml:jar:5.2.5
```

但 `E:\env\maven_repo\org\apache\poi\poi-ooxml\5.2.5\` 下明明有 `poi-ooxml-5.2.5.jar`。

**根因在 `_remote.repositories` 文件。**

Maven 会在每个 artifact 目录下记录"这个文件是从哪个仓库下载的"：

```
poi-ooxml-5.2.5.jar>central=
poi-ooxml-5.2.5.pom>central=
```

**这些是 IDEA 自带的 Maven 下载的，用的是 `central`（中央仓库）。**

而我命令行用的是 `-gs maven-settings.xml`，里面配的镜像 id 是 `aliyunmaven`。

**离线模式下，Maven 会检查"当前配置的仓库（aliyunmaven）有没有下载过这个 artifact"，发现记录里只有 `central`，于是判定"没下过"，拒绝使用。**

**修法**：去掉 `-o`，联网跑一次。Maven 会从 aliyunmaven 重新校验/下载，并更新 `_remote.repositories`。

**这个坑的通用教训**：**同一台机器上有两套 Maven（IDEA 内置 + 命令行）时，本地仓库的元数据会互相干扰。** 尤其是一个用中央仓库、一个用镜像的时候。

### 坑 3：`end` 传当天日期，结果少了一天

第一版 SQL 是：

```sql
and order_time <= #{end}
```

前端传 `begin=2026-10-17&end=2026-10-18`，结果只返回 10-17 的数据，10-18 的一条都没有。

排查：把参数代进去看 SQL —— `order_time <= '2026-10-18'`，MySQL 把它当 `2026-10-18 00:00:00`。10-18 13:00 的订单当然不在范围内。

**改成 `date_add(#{end}, interval 1 day)` + `<`，问题解决。**

**这个坑的隐蔽性在于"不报错，只是少数据"。** 如果测试数据的日期范围选得很宽（比如查一年），你会漏掉最后一天，但总数看起来"差不多"，很容易蒙混过关。

### 坑 4：Excel 下载的文件名是一串乱码

第一版直接写：

```java
response.setHeader("Content-Disposition", "attachment; filename=" + fileName);
```

`fileName` 里有中文（"营业额报表"），浏览器下载下来的是 `%E8%90%A5...xlsx` 或者干脆乱码。

**修法就是 ③ 层讲的 `filename*=UTF-8''` + `URLEncoder`。**

**补充一个前端侧的坑**：用 `fetch` 下载时，`Content-Disposition` 这个响应头在**跨域**情况下默认读不到（浏览器只暴露 6 个"安全"响应头）。服务端要加：

```java
response.setHeader("Access-Control-Expose-Headers", "Content-Disposition");
```

这个项目前后端同源（静态资源打进 jar），所以没踩到。**一旦前后端分离部署，这个坑必踩。**

---

## ⑦ 缺口

| 缺口 | 严重度 | 说明 | 怎么改 |
|---|---|---|---|
| **营业额统计一次请求发 2 条聚合 SQL** | 中 | 两条 SQL 扫同一区间，可以合并成一条 | 用 `sum(case when ...)` 一条出三个指标 |
| **`BusinessDataVO` 有 `newUsers` 字段但从不填** | 低 | 返回 `"newUsers":null`，前端要判空 | 删掉这个字段，或者补上赋值 |
| **`date` 字段类型不统一** | 低 | 营业额/订单是 `String`，用户是 `LocalDate` | SQL 统一用 `date()`，Java 统一用 `LocalDate` |
| **没有 `(order_time, status)` 联合索引** | 中 | 三个统计 SQL 都是 `where status=5 and order_time between ...`，现在全表扫 | `ALTER TABLE orders ADD INDEX idx_time_status (order_time, status)` |
| **导出固定最近 30 天** | 低 | 用户不能选区间 | 加 `begin`/`end` 参数 |
| **Excel 没有合计行、没有百分比格式** | 低 | 完成率 `0.5` 显示成 `0.5` 而不是 `50%` | 用 `CellStyle` 设 `dataFormat` |
| **`round()` 用 double 不用 BigDecimal** | 低 | 统计展示可接受，参与金额计算就不行 | 金额相关换 `BigDecimal` |
| **导出没做权限控制** | 中 | 任何管理员都能导出全量数据 | 按角色限制（财务才能导） |
| **没有导出审计日志** | 低 | 谁导了数据、导了多少，查不到 | 加操作日志表 |

### 「缺联合索引」值得量化

三条统计 SQL 的 `WHERE` 都是：

```sql
where status = 5 and order_time >= ? and order_time < ?
```

现在的索引情况（从建表语句看）：`orders` 表主键是 `id`，`number` 有唯一索引，**`order_time` 和 `status` 都没有索引**。

**这意味着每次查报表都是全表扫描。**

1 万条订单：扫 1 万行，还能接受。
100 万条订单：扫 100 万行，每次报表请求 1~2 秒，四个报表就是 8 秒，页面基本卡死。

**加索引：**

```sql
ALTER TABLE orders ADD INDEX idx_time_status (order_time, status);
```

**为什么是 `(order_time, status)` 这个顺序，不是 `(status, order_time)`？**

联合索引遵循**最左前缀原则**。查询条件是 `status = 5 AND order_time BETWEEN ? AND ?`：

- `(order_time, status)`：先用 `order_time` 的范围缩小，再在范围内过滤 `status`
- `(status, order_time)`：先用 `status` 等值定位，再在 `status=5` 的所有行里按 `order_time` 范围扫

**理论上 `(status, order_time)` 更好**（等值在前，范围在后，能更充分利用索引）。

**但实际要看数据分布**：如果 95% 的订单最终都是 `status=5`，那"用 status 过滤"几乎没缩小范围，反而不如用 `order_time`（报表只查特定时间段，能砍掉 99% 的历史数据）。

**这个项目的场景是"查最近 30 天"，时间过滤的选择性远高于状态过滤**，所以 `(order_time, status)` 更合适。

**面试如果被问"联合索引的顺序怎么定"，答"看选择性，等值条件放前面、范围条件放后面是通用原则，但要结合实际数据分布；报表场景通常时间范围的选择性最高"。**

### 「导出固定 30 天」是个设计问题

```java
LocalDate end = LocalDate.now();
LocalDate begin = end.minusDays(29);
```

导出接口**完全忽略**了前端传的日期。

用户想导"上个月"的报表，导出来是"最近 30 天"；想导"某个促销活动期间"的，也导不出来。

**接口设计上应该和查询接口一致**（都收 `begin`/`end`），现在这样是能力缺失。

---

## ⑧ 面试 30 秒版

> 报表四个接口：营业额、用户、订单、销量 Top10，全部走 XML 写聚合 SQL，因为涉及 `date_format` 分组、`sum(case when)` 条件聚合、join，条件构造器表达不了。
>
> 营业额口径是**只算 `status=5` 已完成订单**，按下单日 `date_format(order_time)` 分组，`sum(amount)` 求和。有两个关键细节：一是日期区间必须写 `order_time < date_add(#{end}, interval 1 day)`，不能写 `<= end` —— 因为 end 传的是 `2026-10-18`，MySQL 当 `00:00:00`，会把当天全天都漏掉；二是完成率和客单价在 Service 层算，要处理除零，而且 `validCount * 1.0` 那个 `1.0` 不能省，否则两个 Integer 相除是整数除法，结果永远是 0。
>
> 用户统计的累计数是"区间前基数 + 逐日累加"，先查一次 `count(*) where create_time < begin` 拿基数，再在内存里累加，比窗口函数可读。
>
> 我抓 SQL 发现营业额统计一次请求发了 2 条聚合 SQL，因为它内部又调了一次订单统计拿总订单数，其实可以用 `sum(case when status=5 then 1 else 0 end)` 一条出三个指标。
>
> Excel 用 POI 的 `XSSFWorkbook`，中文文件名必须用 `filename*=UTF-8''` + `URLEncoder`，否则乱码。数据量大会换 EasyExcel，因为 XSSF 是全量加载内存。

---

## 附：验证脚本

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/admin/employee/login \
  -H "Content-Type: application/json" -d '{"username":"admin","password":"123456"}' \
  | python -c "import sys,json;print(json.load(sys.stdin)['data']['token'])")

# 四个统计接口
curl -s "http://localhost:8080/admin/report/turnoverStatistics?begin=2026-10-01&end=2026-10-27" -H "token: $TOKEN"
curl -s "http://localhost:8080/admin/report/userStatistics?begin=2026-10-01&end=2026-10-27"     -H "token: $TOKEN"
curl -s "http://localhost:8080/admin/report/orderStatistics?begin=2026-10-01&end=2026-10-27"    -H "token: $TOKEN"
curl -s "http://localhost:8080/admin/report/salesTop10?begin=2026-10-01&end=2026-10-27"          -H "token: $TOKEN"

# 导出（-D - 打印响应头，-o 存文件）
curl -s -D - -o report.xlsx "http://localhost:8080/admin/report/export" -H "token: $TOKEN" \
  | grep -iE "HTTP|content-type|content-disposition"
ls -la report.xlsx
```

**验证 xlsx 是真文件的方法**：看文件头是不是 `PK`（zip 格式）：

```bash
xxd report.xlsx | head -1     # 应该输出 504b 0304 ...
```
