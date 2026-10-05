package dev.tradingexecutionlab.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/** An immutable fill reported for an order. */
public record Execution(
        String executionId,
        String orderId,
        BigDecimal quantity,
        BigDecimal price,
        Instant executedAt) {

    public Execution {
        executionId = requireText(executionId, "executionId");
        orderId = requireText(orderId, "orderId");
        quantity = requirePositive(quantity, "quantity");
        price = requirePositive(price, "price");
        Objects.requireNonNull(executedAt, "executedAt");
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static BigDecimal requirePositive(BigDecimal value, String name) {
        Objects.requireNonNull(value, name);
        if (value.signum() <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }
}
