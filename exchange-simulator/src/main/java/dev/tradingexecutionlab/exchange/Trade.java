package dev.tradingexecutionlab.exchange;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/** One matched quantity between a buy order and a sell order. */
public record Trade(
        String tradeId,
        String buyOrderId,
        String sellOrderId,
        BigDecimal quantity,
        BigDecimal price,
        Instant executedAt) {

    public Trade {
        tradeId = requireText(tradeId, "tradeId");
        buyOrderId = requireText(buyOrderId, "buyOrderId");
        sellOrderId = requireText(sellOrderId, "sellOrderId");
        if (buyOrderId.equals(sellOrderId)) {
            throw new IllegalArgumentException("A trade must have two different orders");
        }
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
