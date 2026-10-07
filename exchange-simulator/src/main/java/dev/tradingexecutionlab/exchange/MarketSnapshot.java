package dev.tradingexecutionlab.exchange;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/** One caller-provided top-of-book snapshot; it does not represent a full order book. */
public record MarketSnapshot(
        String symbol,
        BigDecimal bidPrice,
        BigDecimal bidAvailableQuantity,
        BigDecimal askPrice,
        BigDecimal askAvailableQuantity,
        Instant observedAt) {

    public MarketSnapshot {
        symbol = requireText(symbol, "symbol");
        bidPrice = requirePositive(bidPrice, "bidPrice");
        bidAvailableQuantity = requireNonNegative(bidAvailableQuantity, "bidAvailableQuantity");
        askPrice = requirePositive(askPrice, "askPrice");
        askAvailableQuantity = requireNonNegative(askAvailableQuantity, "askAvailableQuantity");
        Objects.requireNonNull(observedAt, "observedAt");
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

    private static BigDecimal requireNonNegative(BigDecimal value, String name) {
        Objects.requireNonNull(value, name);
        if (value.signum() < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
        return value;
    }
}
