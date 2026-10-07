package dev.tradingexecutionlab.exchange;

import java.math.BigDecimal;
import java.util.Objects;

/** Remaining quantity removed from the book by a successful cancel. */
public record CancelResult(String orderId, BigDecimal cancelledQuantity) {
    public CancelResult {
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(cancelledQuantity, "cancelledQuantity");
        if (cancelledQuantity.signum() <= 0) {
            throw new IllegalArgumentException("cancelledQuantity must be positive");
        }
    }
}
