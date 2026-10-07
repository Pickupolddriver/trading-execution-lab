package dev.tradingexecutionlab.exchange;

import java.time.Instant;

/** Immutable event emitted while handling an order command. */
public sealed interface OrderEvent permits OrderAccepted, OrderRejected,
        ExecutionReport, OrderCancelled, CancelRejected {
    Instant eventTime();
}
