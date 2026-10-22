# Project guidance for coding agents

## Project and collaboration

- This is a Java 25 Maven learning project about trading execution, GigaSpaces, and FIX.
- Use the neutral package namespace `dev.tradingexecutionlab`. Never add a person's name, account, email, credentials, or license key to project files.
- The project uses the MIT License. Keep implementation examples small and readable; Lombok is already used in the domain model.
- Do not commit or push unless the user asks. The user explicitly requested compile and unit-test verification for code changes; run the relevant Maven reactor build and UTs after edits.
- Do not add broad frameworks or unrelated architecture while implementing a lesson. Explain behavior and limits in the docs.

## Learning progress

- Current position: Stages 1–4 core lessons are implemented. Stage 5A is in progress: per-order optimistic versions, in-memory execution dedupe, and per-processor command replay are implemented; durable/shared idempotency and distributed concurrency remain. See `docs/stage5-concurrency-idempotency.zh-CN.md`.

- Stage 1 domain/order state: implemented. `Order` includes `clientOrderId`, MARKET/LIMIT, validated `limitPrice`, and `TimeInForce.DAY`.
- Stage 2 local GigaSpaces Space: implemented and verified. Order write/read/change/take and symbol query work. The Space is in-memory and single-writer; the demo exits after closing the Space because the embedded runtime leaves a non-daemon RMI reaper thread.
- Stage 3 partitioning/routing: deterministic synthetic comparison of `orderId`, `clientId`, and `symbol` implemented, with distribution and affinity metrics. This is not a clustered Space benchmark; see `docs/stage3-routing.zh-CN.md`.
- Current exercise: deterministic top-of-book exchange simulator implemented and verified; it returns zero or one fill from one caller-provided snapshot. It is not an OrderBook or matching engine.
- OrderBook: a single-symbol in-memory central limit book with price-time matching, limit remainders, market remainder cancellation, and cancel-by-ID is implemented and verified in `exchange-simulator`. It returns immutable two-sided `Trade` results and does not update domain orders.
- Stage 4 order command/event flow: synchronous in-memory `OrderCommandProcessor` implemented around `OrderBook`, with accept/reject, bilateral execution reports, and cancel success/reject events. Stage 5A now serializes processor calls and replays cached command results inside one JVM; see the Stage 4 and Stage 5 docs for the distinction.
- Stage 5A concurrency/idempotency: per-order versions, consistent snapshots, compare-version execution updates, execution payload checks, command-result replay, and concurrent UTs implemented. This is process-local only; no durable storage or cross-instance coordination.
- Next: continue Stage 5 by designing a shared/persistent state and dedupe commit boundary. The OrderBook remains a single-process object; the routing lesson is a synthetic model, not proof of distributed consistency. See the OrderBook design and walkthrough docs.

## Domain boundaries

- `order-domain` must not depend on GigaSpaces, QuickFIX/J, or a database.
- Keep FIX mapping and session concerns in `execution-gateway`; keep Space mapping in `gigaspaces-grid`.
- `clientId` identifies the customer; `clientOrderId` identifies the customer's request; `orderId` is the internal stable ID.
- `Order.applyExecution` now synchronizes per order, checks execution IDs and supports optimistic versions. Its execution records are only in memory; do not describe this as durable or cross-instance idempotency.
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
