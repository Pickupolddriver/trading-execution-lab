package dev.tradingexecutionlab.exchange;

import java.time.Instant;

public record CancelRejected(String orderId, String cancelRequestId,
                             String reason, Instant eventTime) implements OrderEvent {
}
