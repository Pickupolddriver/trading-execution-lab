package dev.tradingexecutionlab.exchange;

import java.math.BigDecimal;
import java.util.Objects;

/** Read-only summary of the best price level on one side of the book. */
public record BookPriceLevel(BigDecimal price, BigDecimal totalQuantity, int orderCount) {
    public BookPriceLevel {
        Objects.requireNonNull(price, "price");
        Objects.requireNonNull(totalQuantity, "totalQuantity");
        if (totalQuantity.signum() <= 0 || orderCount <= 0) {
            throw new IllegalArgumentException("A visible price level must contain positive quantity and orders");
        }
    }
}
