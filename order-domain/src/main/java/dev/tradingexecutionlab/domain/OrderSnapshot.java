package dev.tradingexecutionlab.domain;

import java.math.BigDecimal;

/** Consistent immutable view of an order's versioned mutable state. */
public record OrderSnapshot(
        String orderId,
        long version,
        OrderStatus status,
        BigDecimal quantity,
        BigDecimal filledQuantity,
        BigDecimal remainingQuantity,
        BigDecimal averageFillPrice,
        String rejectionReason) {
}
