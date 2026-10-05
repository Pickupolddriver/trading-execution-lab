package dev.tradingexecutionlab.domain;

/** Lifecycle states for an order in the execution lab. */
public enum OrderStatus {
    NEW,
    ACKNOWLEDGED,
    PARTIALLY_FILLED,
    FILLED,
    REJECTED,
    CANCELLED;

    public boolean isTerminal() {
        return this == FILLED || this == REJECTED || this == CANCELLED;
    }
}
