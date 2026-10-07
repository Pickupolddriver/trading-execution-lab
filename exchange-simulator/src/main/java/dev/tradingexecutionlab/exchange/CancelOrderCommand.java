package dev.tradingexecutionlab.exchange;

import java.time.Instant;
import java.util.Objects;

public record CancelOrderCommand(String orderId, String cancelRequestId,
                                 Instant requestedAt) implements OrderCommand {
    public CancelOrderCommand {
        orderId = requireText(orderId, "orderId");
        cancelRequestId = requireText(cancelRequestId, "cancelRequestId");
        Objects.requireNonNull(requestedAt, "requestedAt");
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
