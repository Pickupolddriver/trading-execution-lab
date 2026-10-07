package dev.tradingexecutionlab.exchange;

import java.time.Instant;

public record OrderRejected(String orderId, String reason,
                            Instant eventTime) implements OrderEvent {
}
