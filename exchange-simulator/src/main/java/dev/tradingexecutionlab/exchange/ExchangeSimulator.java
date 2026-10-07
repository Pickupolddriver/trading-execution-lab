package dev.tradingexecutionlab.exchange;

import dev.tradingexecutionlab.domain.Execution;
import dev.tradingexecutionlab.domain.Order;
import dev.tradingexecutionlab.domain.OrderStatus;
import dev.tradingexecutionlab.domain.OrderType;
import dev.tradingexecutionlab.domain.Side;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Matches one open order against one supplied top-of-book snapshot. */
public class ExchangeSimulator {

    /**
     * Returns a fill at the displayed best price, capped by displayed size and remaining order size.
     * The caller supplies the fill ID and time, then decides when to apply the returned execution.
     */
    public Optional<Execution> simulateFill(
            Order order,
            MarketSnapshot market,
            String executionId,
            Instant executedAt) {
        Objects.requireNonNull(order, "order");
        Objects.requireNonNull(market, "market");
        requireText(executionId, "executionId");
        Objects.requireNonNull(executedAt, "executedAt");

        if (order.getStatus() != OrderStatus.ACKNOWLEDGED
                && order.getStatus() != OrderStatus.PARTIALLY_FILLED) {
            throw new IllegalStateException("Only acknowledged, open orders can be simulated");
        }
        if (!order.getSymbol().equals(market.symbol())) {
            throw new IllegalArgumentException("Market snapshot is for a different symbol");
        }

        boolean buy = order.getSide() == Side.BUY;
        BigDecimal marketPrice = buy ? market.askPrice() : market.bidPrice();
        BigDecimal availableQuantity = buy
                ? market.askAvailableQuantity()
                : market.bidAvailableQuantity();

        if (availableQuantity.signum() == 0 || !limitAllowsFill(order, buy, marketPrice)) {
            return Optional.empty();
        }

        BigDecimal fillQuantity = order.remainingQuantity().min(availableQuantity);
        return Optional.of(new Execution(
                executionId,
                order.getOrderId(),
                fillQuantity,
                marketPrice,
                executedAt));
    }

    private static boolean limitAllowsFill(Order order, boolean buy, BigDecimal marketPrice) {
        if (order.getOrderType() != OrderType.LIMIT) {
            return true;
        }
        int comparison = order.getLimitPrice().compareTo(marketPrice);
        return buy ? comparison >= 0 : comparison <= 0;
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
