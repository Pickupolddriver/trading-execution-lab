package dev.tradingexecutionlab.exchange;

import java.time.Instant;

public record OrderAccepted(String orderId, String clientOrderId,
                            Instant eventTime) implements OrderEvent {
}
