# Learning Roadmap

This repository is intentionally built in stages. The implementation should follow the concepts rather than jumping directly to a complete system.

## Stage 1 — Domain and Order State

### Concepts
- Order lifecycle
- Execution lifecycle
- Position updates
- Valid vs invalid state transitions
- Domain invariants

### Implementation
- `Order`
- `Execution`
- `Position`
- `OrderStatus`
- `Order.applyExecution(...)`

### Failure questions
- What if the same execution arrives twice?
- What if an older execution arrives after a newer one?
- What if two threads update the same order?

### Exit criteria
A deterministic order state machine with unit tests.

---

## Stage 2 — GigaSpaces Space

### Concepts
- Space
- write / read / take / change
- object identity
- routing

### Implementation
Persist the order state in a local Space before introducing clustering.

### Exit criteria
Orders can be stored, queried, and updated through GigaSpaces.

---

## Stage 3 — Partitioning and Data Affinity

Compare these routing strategies:

- orderId
- clientId
- symbol

Document why a trading system might choose one over another.

### Exit criteria
A partitioning decision with measurable trade-offs.

---

## Stage 4 — Event Processing

Introduce commands and events:

- NewOrder
- CancelOrder
- ExecutionReport

Focus on ordering and deterministic processing.

---

## Stage 5 — Concurrency and Idempotency

Add:

- optimistic versioning
- execution IDs
- idempotency checks
- duplicate suppression

---

## Stage 6 — High Availability

Run primary / backup instances and inject failures.

Experiments:

- kill primary
- kill backup
- restart nodes
- interrupt network connectivity

---

## Stage 7 — Recovery and Persistence

Add PostgreSQL persistence after the in-memory behavior is understood.

Questions:

- What is the source of truth?
- How stale can persisted state be?
- How is the Space rebuilt?

---

## Stage 8 — Mini Execution Service

Integrate:

- gateway
- order processor
- risk engine
- exchange simulator
- position state

---

## Stage 9 — FIX

Add QuickFIX/J only after the domain model is stable.

Messages:

- 35=D NewOrderSingle
- 35=8 ExecutionReport
- 35=F OrderCancelRequest

---

## Stage 10 — Interview Readiness

Be able to explain:

- why state is kept in memory;
- why partitioning matters;
- how failover works;
- how duplicates are prevented;
- how ordering is preserved;
- what happens during network partitions;
- how this architecture differs from ordinary Spring microservices.
