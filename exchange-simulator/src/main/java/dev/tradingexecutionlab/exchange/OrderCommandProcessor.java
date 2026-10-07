package dev.tradingexecutionlab.exchange;

import dev.tradingexecutionlab.domain.Execution;
import dev.tradingexecutionlab.domain.Order;
import dev.tradingexecutionlab.domain.OrderStatus;
import dev.tradingexecutionlab.domain.Side;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Single-threaded in-memory command processor used to teach order/event flow. */
public class OrderCommandProcessor {
    private final Map<String, OrderBook> books = new HashMap<>();
    private final Map<String, Order> orders = new HashMap<>();

    public List<OrderEvent> handle(OrderCommand command) {
        Objects.requireNonNull(command, "command");
        return switch (command) {
            case NewOrderCommand newOrder -> handleNewOrder(newOrder);
            case CancelOrderCommand cancelOrder -> handleCancel(cancelOrder);
        };
    }

    public Order findOrder(String orderId) {
        return orders.get(orderId);
    }

    private List<OrderEvent> handleNewOrder(NewOrderCommand command) {
        Order order = command.order();
        if (order.getStatus() != OrderStatus.NEW) {
            return List.of(new OrderRejected(order.getOrderId(),
                    "Only a NEW order can be submitted", command.receivedAt()));
        }
        if (orders.containsKey(order.getOrderId())) {
            String reason = "Duplicate orderId: " + order.getOrderId();
            order.reject(reason);
            return List.of(new OrderRejected(order.getOrderId(), reason, command.receivedAt()));
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
        return List.copyOf(events);
    }

    private List<OrderEvent> handleCancel(CancelOrderCommand command) {
        Order order = orders.get(command.orderId());
        if (order == null) {
            return List.of(new CancelRejected(command.orderId(), command.cancelRequestId(),
                    "Unknown orderId", command.requestedAt()));
        }
        if (order.getStatus() != OrderStatus.ACKNOWLEDGED
                && order.getStatus() != OrderStatus.PARTIALLY_FILLED) {
            return List.of(new CancelRejected(command.orderId(), command.cancelRequestId(),
                    "Order is not open", command.requestedAt()));
        }

        CancelResult result = books.get(order.getSymbol()).cancel(order.getOrderId()).orElse(null);
        if (result == null) {
            return List.of(new CancelRejected(command.orderId(), command.cancelRequestId(),
                    "No resting quantity to cancel", command.requestedAt()));
        }
        order.cancel();
        return List.of(new OrderCancelled(order.getOrderId(), command.cancelRequestId(),
                result.cancelledQuantity(), "CLIENT_REQUEST", command.requestedAt()));
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
}
