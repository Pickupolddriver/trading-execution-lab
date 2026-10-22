package dev.tradingexecutionlab.domain;

import lombok.Getter;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** An order and the rules that move it through its lifecycle. */
@Getter
public class Order {
    private final String orderId;
    private final String clientId;
    private final String clientOrderId;
    private final String symbol;
    private final Side side;
    private final BigDecimal quantity;
    private final OrderType orderType;
    private final BigDecimal limitPrice;
    private final TimeInForce timeInForce;
    private final Instant createdAt;

    private volatile OrderStatus status = OrderStatus.NEW;
    private volatile BigDecimal filledQuantity = BigDecimal.ZERO;
    private volatile BigDecimal averageFillPrice = BigDecimal.ZERO;
    private volatile String rejectionReason;
    private volatile long version;

    private final Map<String, Execution> appliedExecutions = new HashMap<>();

    public Order(
            String orderId,
            String clientId,
            String clientOrderId,
            String symbol,
            Side side,
            BigDecimal quantity,
            OrderType orderType,
            BigDecimal limitPrice,
            TimeInForce timeInForce,
            Instant createdAt) {
        this.orderId = requireText(orderId, "orderId");
        this.clientId = requireText(clientId, "clientId");
        this.clientOrderId = requireText(clientOrderId, "clientOrderId");
        this.symbol = requireText(symbol, "symbol");
        this.side = Objects.requireNonNull(side, "side");
        this.quantity = requirePositive(quantity, "quantity");
        this.orderType = Objects.requireNonNull(orderType, "orderType");
        this.timeInForce = Objects.requireNonNull(timeInForce, "timeInForce");
        if (orderType == OrderType.LIMIT) {
            this.limitPrice = requirePositive(limitPrice, "limitPrice");
        } else {
            if (limitPrice != null) {
                throw new IllegalArgumentException("Market orders must not specify limitPrice");
            }
            this.limitPrice = null;
        }
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public synchronized void acknowledge() {
        requireStatus(OrderStatus.NEW, "Only a new order can be acknowledged");
        status = OrderStatus.ACKNOWLEDGED;
        version++;
    }

    public synchronized void reject(String reason) {
        requireStatus(OrderStatus.NEW, "Only a new order can be rejected");
        rejectionReason = requireText(reason, "reason");
        status = OrderStatus.REJECTED;
        version++;
    }

    public synchronized void applyExecution(Execution execution) {
        validateExecution(execution);
        Execution previous = appliedExecutions.get(execution.executionId());
        if (previous != null) {
            requireSameExecution(previous, execution);
            return;
        }
        applyNewExecution(execution);
    }

    /** Applies a fill only if the caller's observed order version is still current. */
    public synchronized ExecutionApplyResult applyExecution(Execution execution, long expectedVersion) {
        validateExecution(execution);
        Execution previous = appliedExecutions.get(execution.executionId());
        if (previous != null) {
            requireSameExecution(previous, execution);
            return ExecutionApplyResult.DUPLICATE;
        }
        if (version != expectedVersion) {
            return ExecutionApplyResult.VERSION_CONFLICT;
        }
        applyNewExecution(execution);
        return ExecutionApplyResult.APPLIED;
    }

    /** A consistent view of mutable order state for optimistic read/modify/write. */
    public synchronized OrderSnapshot snapshot() {
        return new OrderSnapshot(orderId, version, status, quantity, filledQuantity,
                remainingQuantity(), averageFillPrice, rejectionReason);
    }

    public synchronized void cancel() {
        if (status != OrderStatus.ACKNOWLEDGED && status != OrderStatus.PARTIALLY_FILLED) {
            throw new IllegalStateException("Only an acknowledged, open order can be cancelled");
        }
        status = OrderStatus.CANCELLED;
        version++;
    }

    public synchronized BigDecimal remainingQuantity() {
        return quantity.subtract(filledQuantity);
    }

    private void applyNewExecution(Execution execution) {
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
        appliedExecutions.put(execution.executionId(), execution);
        version++;
    }

    private void validateExecution(Execution execution) {
        Objects.requireNonNull(execution, "execution");
        if (!orderId.equals(execution.orderId())) {
            throw new IllegalArgumentException("Execution belongs to a different order");
        }
    }

    private static void requireSameExecution(Execution previous, Execution retry) {
        if (!previous.executionId().equals(retry.executionId())
                || !previous.orderId().equals(retry.orderId())
                || previous.quantity().compareTo(retry.quantity()) != 0
                || previous.price().compareTo(retry.price()) != 0
                || !previous.executedAt().equals(retry.executedAt())) {
            throw new IllegalArgumentException("Execution ID was reused with different execution data");
        }
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
