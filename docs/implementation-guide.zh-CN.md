# 后续实现指导（可交给 DeepSeek）

## 固定约束

- Java 25、Maven 多模块，沿用 `dev.tradingexecutionlab` 包名。使用简单 Java 与 Lombok 写法，避免为单一功能添加多层抽象。
- 不加入个人姓名、账号、邮件或许可密钥；保留 MIT License。
- `order-domain` 不依赖 GigaSpaces、QuickFIX/J 或数据库。协议转换放在 `execution-gateway`，Space 数据转换放在 `gigaspaces-grid`。
- 每次只完成一个任务，写明改动文件、业务规则、运行方法、未实现部分。未经要求不提交或推送。
- 每次代码修改后都要用 Java 25 执行相关 Maven 编译与 UT；目前项目依赖可用，不要跳过验证。

## 当前模型边界

`Order` 的输入包括内部订单 ID、客户身份、客户请求 ID、证券、方向、数量、类型、限价、有效方式和创建时间。LIMIT 必须有正数限价；MARKET 不允许限价；目前只有 DAY。

`clientOrderId` 是请求编号，不等于 `clientId`。目前没有持久化唯一性约束；将来应按客户 / 来源及约定的唯一性范围识别重复请求。

`Order.applyExecution` 汇总实际成交量与均价，并在当前对象内按成交 ID 去重。它目前不实现市场撮合、限价违规回报处理、成交撤销 / 更正、并发保护或恢复。模拟器要遵守限价；真实对手方发来异常成交时，如何记录并告警需要另行设计，不能简单丢弃而造成账实不符。

`OrderEntry` 保存订单条件与当前状态，不保存成交 ID 集合。`write` 可以覆盖旧记录；`change` 更新一组字段不等于整个业务流程已经拥有事务保护。现有演示单写入者假设只适用于阶段 2。

## 已完成：阶段 3 分区路由模型

`gigaspaces-grid` 的 `RoutingExperimentLesson` 对比 `orderId`、`clientId` 和 `symbol` 在合成工作集上的分区分布和共置比例。它采用 GigaSpaces 文档中的 hash 路由计算思路，但没有启动多分区 Space 或测实际延迟。运行命令和解读见[阶段 3 路由说明](stage3-routing.zh-CN.md)。

## 已完成：阶段 4 订单命令 / 事件

`exchange-simulator` 的 `OrderCommandProcessor` 以单线程同步方式处理 `NewOrderCommand` / `CancelOrderCommand`，并返回接受 / 拒绝、买卖双方执行回报、撤单确认 / 拒绝。它只在当前 JVM 保存状态，不具备并发、可靠事件投递或崩溃恢复能力。详细规则与验收案例见[阶段 4 订单事件说明](stage4-order-events.zh-CN.md)。

## 下一项可交给 DeepSeek：阶段 5 并发与幂等

先为 `OrderCommandProcessor` 增加显式版本号和同一订单的串行化策略，再设计可重启保留的命令 / execution 去重记录。先选一个小任务实现；不得把 `ConcurrentHashMap` 或 JVM 内 `Set` 描述为跨重启幂等。需要覆盖两个并发成交更新、重复命令、重复 execution 和处理失败后重试，并说明 book 与事件之间的原子性边界。

## 已完成：确定性交易所模拟器（阶段 4A 的预备练习）

本练习在主路线阶段 3 之前作为纯 Java 练习完成。代码位于 `exchange-simulator`，可运行 `mvn -pl exchange-simulator exec:java` 演示。它基于单次 top-of-book 快照，不维护挂单，也不是经典 OrderBook。

### 可复制的任务描述

> 已完成。实现和验收规则见 `exchange-simulator` 源码、README 与 `ExchangeSimulatorTest`。Java 25 编译和 UT 已通过。

### 人工验收情景

| 输入 | 预期 |
| --- | --- |
| BUY LIMIT 100，限价 10.60；ask 10.50，可用量 40 | 返回成交 40 @ 10.50；应用后剩余 60 |
| BUY LIMIT，限价 10.40；ask 10.50 | 不成交 |
| SELL LIMIT，限价 10.50；bid 10.60 | 可成交，数量不超过 bid 可用量 |
| MARKET，可用量为 0 | 不成交 |
| NEW 或已终结订单 | 明确拒绝模拟调用 |
| 相同回报重复应用 | 当前订单对象不重复累计；重启后的去重仍未实现 |

连续调用同一个行情快照不代表有新的可用量。以后若模拟多个订单共享流动性，必须引入行情版本和流动性消耗状态。

## 每次回顾时先回答

1. 这是订单输入、命令、成交事实还是状态快照？
2. 谁分配 ID，重复请求如何识别？
3. 哪个操作会改变状态，失败后怎样重试？
4. 数据只在当前对象 / JVM，还是已经可靠保存？
5. 验收结果来自实际运行，还是仅从代码推断？

## FIX 接入时的参考

- [QuickFIX/J FIX 4.4 字典](https://raw.githubusercontent.com/quickfix-j/quickfixj/master/quickfixj-messages/quickfixj-messages-fix44/src/main/resources/FIX44.xml)
- [FIX 官方订单状态说明](https://www.fixtrading.org/online-specification/order-state-changes/)

内部订单 ID 保持稳定；撤单 / 改单请求使用独立请求编号并关联原请求。会话头、序号、重发与校验和由 QuickFIX/J 和网关处理。DAY 到期要基于选定交易场所的交易日历，不能简单解释成创建后 24 小时。
