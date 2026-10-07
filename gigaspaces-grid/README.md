# Local Space lesson

Uses Java 25 and GigaSpaces 18.0.0. Start from the repository root:

```powershell
mvn -pl gigaspaces-grid -am install
mvn -pl gigaspaces-grid exec:exec
```

The first command builds the required modules and runs real embedded-Space tests. The second launches `SpaceLesson` in a separate Java process. It writes an order, reads it, writes the same ID again, updates fill state with `change`, queries by symbol, and removes the entry with `take`.

Read the source in this order: `SpaceLesson`, `OrderEntry`, `LocalOrderSpace`, `OrderSpaceRepository`. `OrderEntry` is a mutable snapshot with Space ID, routing, and index annotations. Business state changes are calculated by `Order` before being copied to the Space.

This is an in-memory, single-writer lesson. Closing the Space loses its data. The snapshot does not contain the order's applied-execution history, and the repository does not restore a domain aggregate. Partitioning, concurrent writes and recovery are later lessons.

If startup requires a license, obtain a valid GigaSpaces evaluation or production license and set `GS_LICENSE` locally. Do not commit it. A Maven mirror using `mirrorOf=*` must exclude `org.openspaces` to allow the official GigaSpaces repository.

[中文讲解和自检问题](../docs/space-lesson.zh-CN.md)

The routing experiment compares synthetic placement and affinity for `orderId`, `clientId`, and `symbol`. It is a deterministic hash model, not a multi-partition latency benchmark. See the [Stage 3 walkthrough](../docs/stage3-routing.zh-CN.md). Run it after installing reactor dependencies:

```powershell
mvn -pl gigaspaces-grid exec:exec '-Dlesson.mainClass=dev.tradingexecutionlab.grid.RoutingExperimentLesson'
```
