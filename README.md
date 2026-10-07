# trading-execution-lab

[简体中文](README.zh-CN.md)

A hands-on Java trading execution system lab for learning stateful, low-latency trading architecture with GigaSpaces, FIX, risk controls, failure recovery, and production-oriented design.

**Build target:** Java 25 (Maven Compiler Plugin 3.15.0).

The order domain model is implemented. The next hands-on lesson adds an embedded GigaSpaces 18.0.0 Space with order write, read, change, and take operations. See the [Grid module](gigaspaces-grid/README.md) and [Chinese walkthrough](docs/space-lesson.zh-CN.md). Partitioning and the full service architecture remain future work.

> Goal: build enough practical trading-system depth to discuss and implement order lifecycle, execution state, partitioning, high availability, failure recovery, and FIX connectivity in a realistic Java system.

## Learning Roadmap

### Phase 1 — Space & Order State
- Model `Order`, `Execution`, `Position`, `Account`, `RiskLimit`
- Learn GigaSpaces Space basics: write / read / take / change
- Implement the minimal order lifecycle:
  - NEW
  - ACKNOWLEDGED
  - PARTIALLY_FILLED
  - FILLED
  - REJECTED
  - CANCELLED
- Deliverable: an in-memory order state model

### Phase 2 — Partitioning, Routing & Data Affinity
- Learn partitions and routing keys
- Compare routing by `orderId`, `clientId`, and `symbol`
- Understand colocating orders, positions, and risk state
- Deliverable: a documented partitioning decision

### Phase 3 — Order Event Processing
- Introduce order commands and execution events
- Learn polling / notify containers and FIFO concerns
- Model:
  - NewOrder
  - CancelOrder
  - ExecutionReport
- Deliverable: event-driven order processing flow

### Phase 4 — Concurrency & Ordering
- Optimistic locking / versioning
- Idempotency and duplicate event handling
- Out-of-order Execution Reports
- Deterministic state transitions
- Deliverable: concurrency and ordering test cases

### Phase 5 — Primary / Backup & Failover
- Primary-backup replication
- Synchronous replication
- Failover and promotion
- In-flight request behaviour
- Deliverable: kill-primary failure experiment

### Phase 6 — Recovery & Split Brain
- Recovery after process / node failure
- Network partition scenarios
- Split-brain risks
- Duplicate execution prevention
- Deliverable: failure matrix and recovery strategy

### Phase 7 — Persistence & Mirror
- Persist in-memory state to PostgreSQL
- Learn Mirror / async persistence concepts
- Define source-of-truth and recovery boundaries
- Deliverable: persistence and restart recovery

### Phase 8 — Mini Execution Service
Build the end-to-end execution flow:

```mermaid
flowchart LR
    Client[Client] -->|New / Cancel| Gateway[Execution Gateway]
    Gateway --> Grid[(GigaSpaces Grid)]
    Grid --> Order[Order Processor]
    Order --> Risk[Risk Engine]
    Risk -->|Accepted| Exchange[Exchange Simulator]
    Exchange -->|Execution Report| Grid
    Grid --> Position[Position State]
```

- Deliverable: working mini execution service

### Phase 9 — QuickFIX/J Integration
- FIX session basics
- NewOrderSingle (35=D)
- ExecutionReport (35=8)
- OrderCancelRequest (35=F)
- Sequence numbers, resend, duplicate handling
- Deliverable: FIX-enabled gateway

### Phase 10 — Trading System Interview
- Explain architectural trade-offs
- Walk through failure scenarios
- Discuss latency vs consistency vs availability
- Compare GigaSpaces with Redis / Hazelcast / Coherence
- Deliverable: interview-ready system design walkthrough

## Planned Modules

```text
trading-execution-lab/
├── order-domain/
├── execution-gateway/
├── gigaspaces-grid/
├── risk-engine/
├── exchange-simulator/
├── persistence/
├── failure-tests/
└── docs/
```

## Engineering Principles

This project intentionally focuses on trading-system concerns instead of CRUD application patterns:

- explicit order state transitions
- strict ordering where required
- deterministic processing
- idempotency
- low-latency state access
- data affinity
- failure recovery
- observability
- realistic fault injection

## Current Status

- [x] Repository initialized
- [x] Learning roadmap defined
- [x] Maven multi-module skeleton created
- [x] Phase 1: order domain model
- [x] Phase 1: order state machine
- [ ] Phase 1: GigaSpaces local setup
- [ ] Phase 2: partitioning / routing
- [ ] Phase 3: event processing
- [ ] Phase 4: concurrency / ordering
- [ ] Phase 5: primary / backup failover
- [ ] Phase 6: recovery / split brain
- [ ] Phase 7: persistence
- [ ] Phase 8: mini execution service
- [ ] Phase 9: QuickFIX/J
- [ ] Phase 10: interview walkthrough

## How We Will Work

Each learning step should produce:

1. one concrete concept learned;
2. one piece of Java implementation;
3. one failure scenario;
4. one interview question;
5. one visible Git commit.

The intention is to grow this repository incrementally rather than generate the whole system at once.
