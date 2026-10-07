package dev.tradingexecutionlab.exchange;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderCancelled(String orderId, String cancelRequestId,
                             BigDecimal cancelledQuantity, String reason,
                             Instant eventTime) implements OrderEvent {
}
