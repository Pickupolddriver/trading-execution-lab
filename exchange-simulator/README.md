# Exchange simulator and OrderBook lessons

Build and install the domain dependency, then run the OrderBook lesson from the repository root:

```powershell
mvn -pl exchange-simulator -am install
mvn -pl exchange-simulator exec:java
```

It submits two resting asks, then a crossing buy limit. To run the earlier top-of-book snapshot example instead:

```powershell
mvn -pl exchange-simulator exec:java '-Dlesson.mainClass=dev.tradingexecutionlab.exchange.ExchangeSimulatorLesson'
```

`ExchangeSimulator` reads one `MarketSnapshot` and returns at most one `Execution`. A buy consumes the ask; a sell consumes the bid. Limit prices are checked and quantity is capped by both the order remainder and displayed size. The caller applies the returned execution to the order.

This is a teaching example, not a matching engine. It has no order book, shared liquidity consumption, continuous market data, time-in-force expiry, or persistence. Do not reuse one snapshot as new liquidity for multiple orders.

The new `OrderBook` is a small in-memory central limit order book for one symbol. It keeps price levels and FIFO order, returns two-sided `Trade` facts, and leaves domain-order updates to the caller. It has no persistence, concurrency control, DAY expiry, amend flow, or shared state across processes.

See [the implementation guide](../docs/implementation-guide.zh-CN.md) for examples and limits.
