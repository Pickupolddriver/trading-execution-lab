package dev.tradingexecutionlab.exchange;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/** Result of submitting one order to a book. It does not mutate the domain Order. */
public record MatchResult(
        String orderId,
        List<Trade> trades,
        BigDecimal filledQuantity,
        BigDecimal remainingQuantity,
        BigDecimal restingQuantity,
        BigDecimal cancelledQuantity) {

    public MatchResult {
        Objects.requireNonNull(orderId, "orderId");
        trades = List.copyOf(trades);
        Objects.requireNonNull(filledQuantity, "filledQuantity");
        Objects.requireNonNull(remainingQuantity, "remainingQuantity");
        Objects.requireNonNull(restingQuantity, "restingQuantity");
        Objects.requireNonNull(cancelledQuantity, "cancelledQuantity");
    }
}
