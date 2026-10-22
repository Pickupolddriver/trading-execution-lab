package dev.tradingexecutionlab.exchange;

import dev.tradingexecutionlab.domain.Execution;
import dev.tradingexecutionlab.domain.Order;
import dev.tradingexecutionlab.domain.OrderType;
import dev.tradingexecutionlab.domain.OrderStatus;
import dev.tradingexecutionlab.domain.Side;
import dev.tradingexecutionlab.domain.TimeInForce;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Thread-safe, single-JVM command processor. Commands are serialized through one monitor. */
public class OrderCommandProcessor {
    private final Map<String, OrderBook> books = new HashMap<>();
    private final Map<String, Order> orders = new HashMap<>();
    private final Map<RequestKey, CommandRecord> commandResults = new HashMap<>();

    public synchronized List<OrderEvent> handle(OrderCommand command) {
        Objects.requireNonNull(command, "command");
        return switch (command) {
            case NewOrderCommand newOrder -> handleNewOrder(newOrder);
            case CancelOrderCommand cancelOrder -> handleCancel(cancelOrder);
        };
    }

    public synchronized Order findOrder(String orderId) {
        return orders.get(orderId);
    }

    private List<OrderEvent> handleNewOrder(NewOrderCommand command) {
        Order order = command.order();
        RequestKey key = new RequestKey("NEW", order.getClientId(), order.getClientOrderId());
        NewOrderFingerprint fingerprint = NewOrderFingerprint.from(order);
        CommandRecord previous = commandResults.get(key);
        if (previous != null) {
            if (previous.fingerprint().equals(fingerprint)) {
                return previous.events();
            }
            String reason = "clientOrderId was reused with different order terms";
            rejectIfNew(order, reason);
            return List.of(new OrderRejected(order.getOrderId(), reason, command.receivedAt()));
        }

        if (order.getStatus() != OrderStatus.NEW) {
            return remember(key, fingerprint, List.of(new OrderRejected(order.getOrderId(),
                    "Only a NEW order can be submitted", command.receivedAt())));
        }
        if (orders.containsKey(order.getOrderId())) {
            String reason = "Duplicate orderId: " + order.getOrderId();
            order.reject(reason);
            return remember(key, fingerprint,
                    List.of(new OrderRejected(order.getOrderId(), reason, command.receivedAt())));
        }

        order.acknowledge();
        orders.put(order.getOrderId(), order);
        var events = new ArrayList<OrderEvent>();
        events.add(new OrderAccepted(order.getOrderId(), order.getClientOrderId(), command.receivedAt()));

        OrderBook book = books.computeIfAbsent(order.getSymbol(), OrderBook::new);
        MatchResult result = book.submit(order, command.receivedAt());
        for (Trade trade : result.trades()) {
            emitExecutionReports(trade, events);
        }
        if (result.cancelledQuantity().signum() > 0) {
            order.cancel();
            events.add(new OrderCancelled(order.getOrderId(), null, result.cancelledQuantity(),
                    "MARKET_REMAINDER", command.receivedAt()));
        }
        return remember(key, fingerprint, events);
    }

    private List<OrderEvent> handleCancel(CancelOrderCommand command) {
        RequestKey key = new RequestKey("CANCEL", command.orderId(), command.cancelRequestId());
        CancelFingerprint fingerprint = new CancelFingerprint(command.orderId());
        CommandRecord previous = commandResults.get(key);
        if (previous != null) {
            return previous.events();
        }

        Order order = orders.get(command.orderId());
        if (order == null) {
            return remember(key, fingerprint, List.of(new CancelRejected(command.orderId(),
                    command.cancelRequestId(), "Unknown orderId", command.requestedAt())));
        }
        if (order.getStatus() != OrderStatus.ACKNOWLEDGED
                && order.getStatus() != OrderStatus.PARTIALLY_FILLED) {
            return remember(key, fingerprint, List.of(new CancelRejected(command.orderId(),
                    command.cancelRequestId(), "Order is not open", command.requestedAt())));
        }

        CancelResult result = books.get(order.getSymbol()).cancel(order.getOrderId()).orElse(null);
        if (result == null) {
            return remember(key, fingerprint, List.of(new CancelRejected(command.orderId(),
                    command.cancelRequestId(), "No resting quantity to cancel", command.requestedAt())));
        }
        order.cancel();
        return remember(key, fingerprint, List.of(new OrderCancelled(order.getOrderId(),
                command.cancelRequestId(), result.cancelledQuantity(), "CLIENT_REQUEST", command.requestedAt())));
    }

    private void emitExecutionReports(Trade trade, List<OrderEvent> events) {
        emitExecutionReport(trade, trade.buyOrderId(), trade.sellOrderId(), Side.BUY, events);
        emitExecutionReport(trade, trade.sellOrderId(), trade.buyOrderId(), Side.SELL, events);
    }

    private void emitExecutionReport(Trade trade, String orderId, String counterOrderId,
                                     Side side, List<OrderEvent> events) {
        Order order = Objects.requireNonNull(orders.get(orderId), "Trade refers to an unknown order");
        Execution execution = new Execution(trade.tradeId() + ":" + side, orderId,
                trade.quantity(), trade.price(), trade.executedAt());
        order.applyExecution(execution);
        events.add(new ExecutionReport(orderId, counterOrderId, trade.tradeId(), side, execution,
                order.getFilledQuantity(), order.remainingQuantity(), order.getStatus(), trade.executedAt()));
    }

    private static void rejectIfNew(Order order, String reason) {
        if (order.getStatus() == OrderStatus.NEW) {
            order.reject(reason);
        }
    }

    private List<OrderEvent> remember(RequestKey key, Object fingerprint, List<OrderEvent> events) {
        List<OrderEvent> immutableEvents = List.copyOf(events);
        commandResults.put(key, new CommandRecord(fingerprint, immutableEvents));
        return immutableEvents;
    }

    private record RequestKey(String commandType, String scope, String requestId) {
    }

    private record CommandRecord(Object fingerprint, List<OrderEvent> events) {
    }

    private record NewOrderFingerprint(String symbol, Side side, String quantity,
                                       OrderType orderType, String limitPrice,
                                       TimeInForce timeInForce) {
        private static NewOrderFingerprint from(Order order) {
            return new NewOrderFingerprint(order.getSymbol(), order.getSide(),
                    normalize(order.getQuantity()), order.getOrderType(),
                    normalize(order.getLimitPrice()), order.getTimeInForce());
        }

        private static String normalize(BigDecimal value) {
            return value == null ? null : value.stripTrailingZeros().toPlainString();
        }
    }

    private record CancelFingerprint(String orderId) {
    }
}
