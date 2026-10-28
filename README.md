# 星河影城票务平台 · 核心模块实现

按 `影院票务平台-核心模块实现方案.md` 落地的可运行代码。Java 17 + Spring Boot 3.2.6 + MyBatis-Plus + Redis Bitmap。

## 1. 快速启动

前置：JDK 17、Maven 3.9+、Redis（默认 `127.0.0.1:6379`）。

```bash
# 1) 起 Redis（本机已有 D:\Redis\redis-server.exe 时）
redis-server --port 6379

# 2) 构建（含单元测试）
mvn package

# 3) 启动（默认 H2 内存库，自动建表 + 造演示数据）
java -jar cinema-app/target/cinema-app.jar --server.port=8080
```

> 注意：若 shell 里存在 `SERVER__PORT` 环境变量，Spring 的宽松绑定会把它当成 `server.port`，
> 覆盖配置文件里的 8080。显式传 `--server.port=8080` 即可（命令行参数优先级最高）。

启动后日志会打印演示数据的 id：

```
演示数据初始化完成 {cinemaId=1, hallIds=[1,2,3], movieIds=[1,2,3], sessionIds=[1..5], memberId=1, couponId=1, priceVersion=1}
座位总数：1号厅 200，IMAX厅 360，情侣厅 96
```

主链路体验：

```bash
# 座位图（含三态、分区价格、连座推荐）
curl "http://127.0.0.1:8080/session/1/seatmap?need=3"

# 锁座下单
curl -X POST http://127.0.0.1:8080/order/lock -H "Content-Type: application/json" -d '{
  "memberId":1,"sessionId":1,"seatIds":[91,92,93],"requestId":"req-001"}'

# 模拟支付回调（走与真实渠道完全相同的处理路径）
curl -X POST http://127.0.0.1:8080/payment/SOxxxxxxxxxxxxx/pay

# 对账（文档 8.2 的三条 SQL）
curl http://127.0.0.1:8080/order/admin/reconcile
```

### MySQL 模式

```bash
mysql -uroot -p -e "CREATE DATABASE cinema DEFAULT CHARACTER SET utf8mb4"
mysql -uroot -p cinema < cinema-app/src/main/resources/db/schema-mysql.sql
set MYSQL_USER=root
set MYSQL_PASSWORD=你的密码
java -jar cinema-app/target/cinema-app.jar --spring.profiles.active=mysql --server.port=8080
```

H2 默认是**内存库**，每次启动重建（演示可重复，不用清数据）。

## 2. 工程结构

```
cinema-parent
├── cinema-common      Result/BizException/枚举、Redis key 与 Lua 脚本、位运算工具、分布式锁、延迟消息接口
├── cinema-cinema      影院 · 影厅 · 座位（建厅自动生成座位 + 分区）
├── cinema-movie       影片 · 上下架
├── cinema-schedule    排片 · 冲突检测 · 批量导入 · 价格规则与试算
├── cinema-ticket      座位图 · 原子锁座 · 出票 · bitmap 重建（唯一可写 bitmap 的模块）
├── cinema-payment     支付单 · 退款 · 支付回调入口
├── cinema-member      会员 · 优惠券
├── cinema-order       订单 · 状态机 · 超时释放 · 退票 · 改签 · 对账
└── cinema-app         启动器 · 配置 · H2/MySQL 建表脚本 · 演示数据
```

依赖方向与文档一致：`order → ticket / payment / schedule`，`ticket → schedule → cinema/movie`。
七个服务模块在**同一进程**内装配（`cinema-app`），模块边界与包名保持文档命名；要拆独立进程时，
给每个模块补一个 `main` 类即可，业务代码不用改。

## 3. 文档章节 → 代码位置

| 文档 | 实现 |
|---|---|
| 1.1 不预生成场次座位 | 无 `session_seat` 表；`Redis seat:occ:{sessionId}` 为唯一事实来源，`ticket` 只存已售 |
| 1.2 bitmap 偏移 | `SeatBitmapUtil.offset()` = `(rowNo-1)*colCount+(colNo-1)`，单测锁死 |
| 1.3 三态 | bitmap 只表达「不可售」；`SeatMapService` 用 `ticket` 表把 1 拆成「已锁/已售」 |
| 1.4 表结构 | `db/schema-h2.sql`、`db/schema-mysql.sql`；`uk_session_seat` 是最后一道防线 |
| 2.2 Lua 原子锁座 | `LuaScripts.LOCK_SEATS`（两轮循环，先全查后全写） |
| 2.4 幂等 | `RedisKeys.idemOrder` + `order.request_id` 唯一索引 |
| 2.5 边界 | 越界/串厅/损坏座位校验在 `TicketService.resolveOffsets`，场次可售校验在 `SessionService.getSellable` |
| 3.1 一次取回整场 | `SeatBitmapRedis.loadRaw()` 单次 GET，本地 `bytesToWords` |
| 3.2 连座公式 | `SeatBitmapUtil.startsOfRun()`，O(need) |
| 3.3 候选打分 | `SeatMapService.collectCandidates()`，`colScore*2 + rowScore` |
| 3.4 64 座边界 | `HallCreateRequest.colCount ≤ 64`，超出直接拒绝（不做多 word） |
| 4.1 双通道释放 | 主：`LocalDelayMessagePublisher`（15 分钟）；兜底：`TimeoutReleaseJob`（每分钟扫表，含 1 分钟偏移） |
| 4.2 CAS 释放 | `OrderMapper.cancelIfExpired()`，`affectedRows==1` 才清位 |
| 4.3 支付/释放撞车 | `OrderPaymentCallbackService.onPaySuccess()`：CAS 失败且订单已取消 → 立即退款 |
| 4.4 批量清位 | `LuaScripts.RELEASE_SEATS`，并跳过已售座位 |
| 4.5 bitmap 重建 | `POST /admin/bitmap/rebuild`，重建期间 `session.rebuilding=1` 停售 |
| 5.2 区间重叠 | `SessionMapper.findConflicts()`：`start_time < newEnd AND end_time > newStart` |
| 5.3 并发排片 | Redisson 锁 `lock:schedule:hall:{hallId}` |
| 5.4 批量导入 | `SessionService.createBatch()`：批内自检 → 按 hall 各加一次锁 → 事务内逐场查冲突 |
| 6.1 手续费阶梯 | `refund_policy` + `OrderRefundService.resolvePolicy()`，按实付金额算、向下取整到分 |
| 6.2 先占后放 | `OrderChangeService.change()`，旧座位释放失败只记日志，由 `repairLeakedOldSeats()` 补清 |
| 6.3 差价 | 需补款 → `change_record.status=2` + 延迟消息超时回滚；需退款 → 立即生效 + 退款 |
| 6.4 状态机 | `OrderStateMachine` 校验 + Mapper 里所有 UPDATE 都带原状态条件 |
| 7.2 价格快照 | `order_item.price` / `ticket.price` 落库即固定 |
| 8.2 校验 SQL | `ReconcileMapper` + `GET /order/admin/reconcile` |

## 4. 接口清单

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/cinema/list`、`/cinema/{id}`、`/cinema/{id}/halls` | 影院 |
| POST | `/cinema`、`/cinema/hall` | 建影院、建厅（自动生成座位） |
| GET | `/cinema/hall/{id}`、`/cinema/hall/{id}/seats` | 影厅与座位 |
| GET | `/movie/list`、`/movie/{id}`；POST `/movie` | 影片 |
| GET | `/session/{id}`、`/session/list`、`/session/by-movie` | 场次 |
| POST | `/session`、`/session/batch` | 排片、批量排片（全成功或全失败） |
| PUT | `/session/{id}/status` | 停售/取消 |
| GET | `/price/rule`、`/price/version/list`、`/price/preview` | 价格规则与试算 |
| POST | `/price/rule/version` | 调价（生成新版本） |
| GET | **`/session/{id}/seatmap`** | 座位图 + 三态 + 分区价格 + 连座推荐 |
| POST | **`/order/lock`** | 锁座下单 |
| GET | `/order/{id}`、`/order/no/{orderNo}`、`/order/list` | 订单详情 |
| POST | `/order/{id}/cancel`、`/order/{id}/refund` | 取消、退票 |
| POST | `/order/change`、`/order/change/{changeId}/pay` | 改签、补款确认 |
| GET | `/order/{id}/changes`、`/order/no/{orderNo}/refunds` | 改签/退款记录 |
| POST | **`/payment/callback`** | 渠道支付回调 |
| POST | `/payment/{orderNo}/pay` | 模拟支付（同一条处理路径） |
| GET | `/payment/{orderNo}`、`/payment/refund/{orderNo}` | 支付单、退款流水 |
| GET | `/member/{id}`、`/member/{id}/coupons`；POST `/member`、`/member/coupon` | 会员、优惠券 |
| GET | `/order/admin/reconcile` | 对账检查 |
| POST | `/admin/bitmap/rebuild`、`/order/admin/change/repair` | bitmap 重建、座位泄漏补清 |

## 5. 验证脚本（已实跑通过）

```bash
python scripts/e2e_test.py          # 30 项：座位图/幂等/抢座/支付出票/退票/改签/排片冲突/定价/对账
python scripts/concurrency_test.py 30   # 30 线程抢同一座位 → 1 成功、0 重复售出
python scripts/e2e_timeout.py       # 需以 --cinema.order.lock-timeout-minutes=1 启动，验证超时释放与支付撞车退款
```

实跑结果：`e2e_test.py` 30/30 PASS；`e2e_timeout.py` 14/14 PASS；
并发 30 线程抢座成功 1 单、被拒 29 单、对账 `duplicateSales=0`；
`POST /admin/bitmap/rebuild` 重建 7 个场次后对账仍然健康。
单元测试 13 个（`SeatBitmapUtil` 位运算 10 个、`MoneyUtil` 金额 3 个）全绿。

## 6. 文档未明确 / 有冲突处的取舍（关键假设）

1. **微服务运行形态**：文档要求 Nacos/Gateway/Feign/Sentinel，但本机无 Docker 与注册中心。
   取舍：保留七个 Maven 模块与服务边界、包名、表归属，单进程装配，服务间用 Spring Bean 直调而非 Feign。
   影响：网络分区、熔断限流不在考察范围；拆进程时只需补 main 类和 Feign 客户端。
2. **RocketMQ 延迟消息 → 本地延迟队列**：抽了 `DelayMessagePublisher` 接口，
   默认实现是「落 `delay_task` 表 + JVM 定时 + 到期补偿扫描 + 失败重试 3 次」，语义与延迟消息对齐；
   接 RocketMQ 只需再加一个实现 `bean`。
3. **XXL-Job → `@Scheduled`**：扫表 SQL 与文档完全一致（含 1 分钟偏移），只是调度器换了。
4. **`order_item` / `change_record` / `payment` / `refund` / `member` / `coupon` / `delay_task` 无 DDL**：
   按职责补建，字段见 `db/schema-mysql.sql` 注释。
5. **`refund_record` 与 `refund` 的区分**：`refund_record`（order 侧）记录「业务退票 + 手续费」，
   `refund`（payment 侧）记录「资金退款流水」，一次退票两条记录。
6. **`session.rebuilding` 字段**：文档 4.5 提到但 1.4 的 DDL 没有，已补列。
7. **`order` 表补 4 列**：`request_id`（文档 2.4 要求唯一索引）、`pay_no`、`paid_at`、`coupon_id`。
8. **动态定价公式**：7.1 公式是「基础价 × 时段系数 × 厅型系数 × 分区系数」，
   但 `price_rule` 的唯一键把四个维度合成一行、只有一个 `multiplier`。按表结构实现：
   一次查询得到一个综合系数，`最终价 = base_price × multiplier`，两者等价。
9. **退票要能被再次售出**：`ticket` 的唯一索引会让退掉的座位永久无法重售，
   所以退票时删 `ticket` 行（审计信息保留在 `refund_record`），然后 `SETBIT=0`。
10. **释放已售座位**：文档 1.3 只说「释放 → SETBIT=0」。实现里释放前先查 `ticket`，
    已售票一律不释放，防止迟到的释放请求把已出票座位清掉。
11. **bitmap 重建的范围**：文档 4.5 只重建「已售」。实现里额外并入「未超时的待支付订单占座」，
    否则重建会把在途订单的锁抹掉，可能超卖。
12. **改签只允许已出票（30）**：文档状态图是 `30 → 70`；`20` 在实现里是支付回调内部的瞬时态。
13. **改签补款**：mock 支付，`POST /order/change/{changeId}/pay` 直接确认，不生成独立补款支付单。
14. **支付金额校验**：回调金额与 `payment.amount`、`order.pay_amount` 都做比对，不一致直接拒绝。
15. **`recommend` 字段**：文档示例是列号数组，但缺排号。返回 `recommend` + 平行的 `recommendRows`，
    保持向后兼容（只加字段不改结构）。
16. **时段划分**：`MIDNIGHT 0-6 / MORNING 6-12 / NOON 12-18 / EVENING 18-24`（文档只给了枚举名）。
17. **座位分区**：两侧 `max(1, col/5)` 列为 C 区，前排 30% 与后排 25% 为 A 区，其余 B 区；情侣厅所有座位 `seat_type=2`。
18. **节假日**：`cinema.pricing.holidays` 配置项（`yyyy-MM-dd`），无内置表。
19. **`colCount ≤ 64`**：超出单 word 上限直接在建设厅/建厅接口拒绝，走文档 3.4 的「明确技术边界」路线。
20. **H2 内存库**：默认 profile 用 H2（MySQL 兼容模式），每次启动重建；生产走 `mysql` profile。

## 7. 尚未覆盖（明确留白）

- **Gateway / Nacos / Sentinel / OpenFeign**：未接，服务调用是同进程方法调用。
- **真实支付渠道**：只有 MOCK 通道，无签名验签、无异步退款通知（退款直接置成功）。
- **取票码核销、开场自动流转 40 已观影**：表字段与状态机留好，未做定时任务。
- **优惠券叠加/过期状态刷新**：单券抵扣已实现，未做券状态定时过期。
- **压测**：只跑了 30 线程抢座的功能性验证，没有 JMeter 5000 线程 + P99 报告；文档 8.1 的指标未产出。
- **接口鉴权**：无登录态，`memberId` 由前端传入（演示用），生产需网关层鉴权。
- **可观测性**：无链路追踪/指标埋点，仅 SLF4J 日志。
- **分库分表、多影院隔离**：单库单表，`cinema_id` 只做数据归属，未做租户隔离。
