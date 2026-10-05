# Target Architecture

The final system is expected to evolve toward this shape:

```text
                    +------------------+
                    |      Client      |
                    +--------+---------+
                             |
                             | NewOrder / Cancel
                             v
                    +------------------+
                    | Execution Gateway|
                    +--------+---------+
                             |
                             v
                 +------------------------+
                 |     GigaSpaces Grid    |
                 |                        |
                 | Order / Execution      |
                 | Position / Risk State  |
                 +----+--------------+----+
                      |              |
            +---------+--+        +--+----------+
            |Order Proc. |        | Risk Engine |
            +------+-----+        +------+------+
                   |                     |
                   +----------+----------+
                              |
                              v
                    +-------------------+
                    | Exchange Simulator|
                    +---------+---------+
                              |
                              | ExecutionReport
                              v
                    +-------------------+
                    |  GigaSpaces Grid  |
                    +---------+---------+
                              |
                              v
                    +-------------------+
                    |    Persistence    |
                    |    PostgreSQL     |
                    +-------------------+
```

## Module Responsibilities

### order-domain
Pure trading domain model. No infrastructure dependency should be required in the first implementation.

### execution-gateway
External entry point for order commands. Later this can host REST and FIX adapters.

### gigaspaces-grid
GigaSpaces-specific configuration, entries, routing, partitioning, and event-processing integration.

### risk-engine
Pre-trade checks and risk state.

### exchange-simulator
Simulated venue that generates acknowledgements, rejects, partial fills, and fills.

### persistence
Database persistence and recovery integration.

### failure-tests
Integration and fault-injection scenarios for failover, duplicates, ordering, and recovery.

## Design Rule

Do not let infrastructure concerns leak into the core trading domain unless there is a strong reason.

A useful dependency direction is:

```text
infrastructure -> application -> domain
```

not:

```text
domain -> GigaSpaces / FIX / PostgreSQL
```

This lets the project teach both trading-domain design and distributed-system design separately before integrating them.
