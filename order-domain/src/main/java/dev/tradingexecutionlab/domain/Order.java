package dev.tradingexecutionlab.domain;

import lombok.Getter;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** An order and the rules that move it through its lifecycle. */
@Getter
public class Order {
    private final String orderId;
    private final String clientId;
    private final String symbol;
    private final Side side;
    private final BigDecimal quantity;
    private final Instant createdAt;

    private OrderStatus status = OrderStatus.NEW;
    private BigDecimal filledQuantity = BigDecimal.ZERO;
    private BigDecimal averageFillPrice = BigDecimal.ZERO;
    private String rejectionReason;

    @Getter(lombok.AccessLevel.NONE)
    private final Set<String> appliedExecutionIds = new HashSet<>();

    public Order(
            String orderId,
            String clientId,
            String symbol,
            Side side,
            BigDecimal quantity,
            Instant createdAt) {
        this.orderId = requireText(orderId, "orderId");
        this.clientId = requireText(clientId, "clientId");
        this.symbol = requireText(symbol, "symbol");
        this.side = Objects.requireNonNull(side, "side");
        this.quantity = requirePositive(quantity, "quantity");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public void acknowledge() {
        requireStatus(OrderStatus.NEW, "Only a new order can be acknowledged");
        status = OrderStatus.ACKNOWLEDGED;
    }

    public void reject(String reason) {
        requireStatus(OrderStatus.NEW, "Only a new order can be rejected");
        rejectionReason = requireText(reason, "reason");
        status = OrderStatus.REJECTED;
    }

    public void applyExecution(Execution execution) {
        Objects.requireNonNull(execution, "execution");
        if (!orderId.equals(execution.orderId())) {
            throw new IllegalArgumentException("Execution belongs to a different order");
        }
        if (appliedExecutionIds.contains(execution.executionId())) {
            return;
        }
        if (status != OrderStatus.ACKNOWLEDGED && status != OrderStatus.PARTIALLY_FILLED) {
            throw new IllegalStateException("Executions can only be applied to acknowledged, open orders");
        }

        BigDecimal newFilledQuantity = filledQuantity.add(execution.quantity());
        if (newFilledQuantity.compareTo(quantity) > 0) {
            throw new IllegalArgumentException("Execution quantity exceeds the order's remaining quantity");
        }

        BigDecimal oldNotional = averageFillPrice.multiply(filledQuantity);
        BigDecimal newNotional = execution.price().multiply(execution.quantity());
        averageFillPrice = oldNotional.add(newNotional)
                .divide(newFilledQuantity, MathContext.DECIMAL128);
        filledQuantity = newFilledQuantity;
        status = filledQuantity.compareTo(quantity) == 0
                ? OrderStatus.FILLED
                : OrderStatus.PARTIALLY_FILLED;
        appliedExecutionIds.add(execution.executionId());
    }

    /** Call after cancellation has been confirmed. */
    public void cancel() {
        if (status != OrderStatus.ACKNOWLEDGED && status != OrderStatus.PARTIALLY_FILLED) {
            throw new IllegalStateException("Only an acknowledged, open order can be cancelled");
        }
        status = OrderStatus.CANCELLED;
    }

    public BigDecimal remainingQuantity() {
        return quantity.subtract(filledQuantity);
    }

    private void requireStatus(OrderStatus expected, String message) {
        if (status != expected) {
            throw new IllegalStateException(message + " (current status: " + status + ")");
        }
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
