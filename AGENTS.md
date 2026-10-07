# Project guidance for coding agents

## Project and collaboration

- This is a Java 25 Maven learning project about trading execution, GigaSpaces, and FIX.
- Use the neutral package namespace `dev.tradingexecutionlab`. Never add a person's name, account, email, credentials, or license key to project files.
- The project uses the MIT License. Keep implementation examples small and readable; Lombok is already used in the domain model.
- Do not commit or push unless the user asks. The user explicitly requested compile and unit-test verification for code changes; run the relevant Maven reactor build and UTs after edits.
- Do not add broad frameworks or unrelated architecture while implementing a lesson. Explain behavior and limits in the docs.

## Learning progress

- Current position: Stages 1–4 core lessons are implemented; Stage 5 (concurrency, ordering, and idempotency) is the next stage and has not started. The latest OrderBook walkthrough and data-structure trade-offs are in `docs/order-book-walkthrough.zh-CN.md`.

- Stage 1 domain/order state: implemented. `Order` includes `clientOrderId`, MARKET/LIMIT, validated `limitPrice`, and `TimeInForce.DAY`.
- Stage 2 local GigaSpaces Space: implemented and verified. Order write/read/change/take and symbol query work. The Space is in-memory and single-writer; the demo exits after closing the Space because the embedded runtime leaves a non-daemon RMI reaper thread.
- Stage 3 partitioning/routing: deterministic synthetic comparison of `orderId`, `clientId`, and `symbol` implemented, with distribution and affinity metrics. This is not a clustered Space benchmark; see `docs/stage3-routing.zh-CN.md`.
- Current exercise: deterministic top-of-book exchange simulator implemented and verified; it returns zero or one fill from one caller-provided snapshot. It is not an OrderBook or matching engine.
- OrderBook: a single-symbol in-memory central limit book with price-time matching, limit remainders, market remainder cancellation, and cancel-by-ID is implemented and verified in `exchange-simulator`. It returns immutable two-sided `Trade` results and does not update domain orders.
- Stage 4 order command/event flow: single-threaded in-memory `OrderCommandProcessor` implemented around `OrderBook`, with accept/reject, bilateral execution reports, and cancel success/reject events. It is not durable or concurrent; see `docs/stage4-order-events.zh-CN.md`.
- Next main roadmap stage: Stage 5 concurrency, ordering, and idempotency. The OrderBook remains a single-process object; the routing lesson is a synthetic model, not proof of distributed consistency. See `docs/order-book-design.zh-CN.md` for design boundaries and `docs/order-book-walkthrough.zh-CN.md` for the matching and data-structure explanation.

## Domain boundaries

- `order-domain` must not depend on GigaSpaces, QuickFIX/J, or a database.
- Keep FIX mapping and session concerns in `execution-gateway`; keep Space mapping in `gigaspaces-grid`.
- `clientId` identifies the customer; `clientOrderId` identifies the customer's request; `orderId` is the internal stable ID.
- `Order.applyExecution` currently keeps duplicate execution IDs only in memory. Do not describe this as durable or concurrent idempotency.
- MARKET/LIMIT and DAY are currently the only supported order terms. DAY expiry behavior is not implemented.
- A snapshot `OrderEntry` is not a recoverable order aggregate or execution ledger.

## Verification

From the repository root, use Java 25 and run the relevant Maven reactor build, for example:

```powershell
mvn -pl exchange-simulator -am test
```

For the current GigaSpaces lesson, use:

```powershell
mvn -pl gigaspaces-grid -am install
mvn -pl gigaspaces-grid exec:exec
```

Run the OrderBook lesson with `mvn -pl exchange-simulator exec:java` after building/installing its `order-domain` dependency.

Run the synthetic routing experiment with `mvn -pl gigaspaces-grid exec:exec '-Dlesson.mainClass=dev.tradingexecutionlab.grid.RoutingExperimentLesson'` after building/installing its reactor dependencies.

GigaSpaces artifacts must resolve from `https://maven-repository.openspaces.org`; Maven mirrors must not redirect that repository to an incompatible mirror. Never add a license key to the repository.
