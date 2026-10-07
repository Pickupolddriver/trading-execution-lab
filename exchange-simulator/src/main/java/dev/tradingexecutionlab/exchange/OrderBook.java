package dev.tradingexecutionlab.exchange;

import dev.tradingexecutionlab.domain.Order;
import dev.tradingexecutionlab.domain.OrderStatus;
import dev.tradingexecutionlab.domain.OrderType;
import dev.tradingexecutionlab.domain.Side;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/** A single-symbol, in-memory limit order book with price-time priority. */
public class OrderBook {
    private final String symbol;
    private final TreeMap<BigDecimal, LinkedHashMap<String, RestingOrder>> bids =
            new TreeMap<>(Comparator.reverseOrder());
    private final TreeMap<BigDecimal, LinkedHashMap<String, RestingOrder>> asks = new TreeMap<>();
    private final Map<String, RestingOrder> openOrders = new HashMap<>();
    private final Set<String> submittedOrderIds = new HashSet<>();
    private long nextTradeNumber = 1;

    public OrderBook(String symbol) {
        this.symbol = requireText(symbol, "symbol");
    }

    public String getSymbol() {
        return symbol;
    }

    /** Matches an acknowledged order and rests an unfilled limit remainder. */
    public MatchResult submit(Order order, Instant executedAt) {
        Objects.requireNonNull(order, "order");
        Objects.requireNonNull(executedAt, "executedAt");
        if (!symbol.equals(order.getSymbol())) {
            throw new IllegalArgumentException("Order belongs to a different symbol");
        }
        if (order.getStatus() != OrderStatus.ACKNOWLEDGED
                && order.getStatus() != OrderStatus.PARTIALLY_FILLED) {
            throw new IllegalStateException("Only acknowledged, open orders can be submitted");
        }
        if (!submittedOrderIds.add(order.getOrderId())) {
            throw new IllegalArgumentException("Order has already been submitted: " + order.getOrderId());
        }

        BigDecimal initialRemaining = order.remainingQuantity();
        if (initialRemaining.signum() <= 0) {
            throw new IllegalStateException("Order has no remaining quantity");
        }

        BigDecimal remaining = initialRemaining;
        var trades = new java.util.ArrayList<Trade>();
        TreeMap<BigDecimal, LinkedHashMap<String, RestingOrder>> oppositeBook = oppositeBook(order.getSide());

        while (remaining.signum() > 0 && !oppositeBook.isEmpty()) {
            Map.Entry<BigDecimal, LinkedHashMap<String, RestingOrder>> bestLevel = oppositeBook.firstEntry();
            BigDecimal restingPrice = bestLevel.getKey();
            if (!canMatch(order, restingPrice)) {
                break;
            }

            RestingOrder restingOrder = bestLevel.getValue().values().iterator().next();
            BigDecimal fillQuantity = remaining.min(restingOrder.remainingQuantity);
            String buyOrderId = order.getSide() == Side.BUY ? order.getOrderId() : restingOrder.orderId;
            String sellOrderId = order.getSide() == Side.SELL ? order.getOrderId() : restingOrder.orderId;
            trades.add(new Trade(nextTradeId(), buyOrderId, sellOrderId,
                    fillQuantity, restingPrice, executedAt));

            remaining = remaining.subtract(fillQuantity);
            restingOrder.remainingQuantity = restingOrder.remainingQuantity.subtract(fillQuantity);
            if (restingOrder.remainingQuantity.signum() == 0) {
                removeRestingOrder(restingOrder);
            }
        }

        BigDecimal filledQuantity = initialRemaining.subtract(remaining);
        BigDecimal restingQuantity = BigDecimal.ZERO;
        BigDecimal cancelledQuantity = BigDecimal.ZERO;
        if (remaining.signum() > 0) {
            if (order.getOrderType() == OrderType.LIMIT) {
                addRestingOrder(order, remaining);
                restingQuantity = remaining;
            } else {
                cancelledQuantity = remaining;
            }
        }

        return new MatchResult(order.getOrderId(), trades, filledQuantity,
                remaining, restingQuantity, cancelledQuantity);
    }

    /** Removes an order's remaining displayed quantity; an absent or already filled order returns empty. */
    public Optional<CancelResult> cancel(String orderId) {
        requireText(orderId, "orderId");
        RestingOrder restingOrder = openOrders.get(orderId);
        if (restingOrder == null) {
            return Optional.empty();
        }

        BigDecimal cancelledQuantity = restingOrder.remainingQuantity;
        removeRestingOrder(restingOrder);
        return Optional.of(new CancelResult(orderId, cancelledQuantity));
    }

    public Optional<BookPriceLevel> bestBid() {
        return bestPriceLevel(bids);
    }

    public Optional<BookPriceLevel> bestAsk() {
        return bestPriceLevel(asks);
    }

    private boolean canMatch(Order incoming, BigDecimal restingPrice) {
        if (incoming.getOrderType() == OrderType.MARKET) {
            return true;
        }
        int comparison = incoming.getLimitPrice().compareTo(restingPrice);
        return incoming.getSide() == Side.BUY ? comparison >= 0 : comparison <= 0;
    }

    private TreeMap<BigDecimal, LinkedHashMap<String, RestingOrder>> oppositeBook(Side side) {
        return side == Side.BUY ? asks : bids;
    }

    private TreeMap<BigDecimal, LinkedHashMap<String, RestingOrder>> sameSideBook(Side side) {
        return side == Side.BUY ? bids : asks;
    }

    private void addRestingOrder(Order order, BigDecimal remainingQuantity) {
        RestingOrder restingOrder = new RestingOrder(
                order.getOrderId(), order.getSide(), order.getLimitPrice(), remainingQuantity);
        sameSideBook(order.getSide())
                .computeIfAbsent(restingOrder.price, ignored -> new LinkedHashMap<>())
                .put(restingOrder.orderId, restingOrder);
        openOrders.put(restingOrder.orderId, restingOrder);
    }

    private void removeRestingOrder(RestingOrder restingOrder) {
        TreeMap<BigDecimal, LinkedHashMap<String, RestingOrder>> sameSide = sameSideBook(restingOrder.side);
        LinkedHashMap<String, RestingOrder> level = sameSide.get(restingOrder.price);
        level.remove(restingOrder.orderId);
        openOrders.remove(restingOrder.orderId);
        if (level.isEmpty()) {
            sameSide.remove(restingOrder.price);
        }
    }

    private Optional<BookPriceLevel> bestPriceLevel(
            TreeMap<BigDecimal, LinkedHashMap<String, RestingOrder>> side) {
        Map.Entry<BigDecimal, LinkedHashMap<String, RestingOrder>> best = side.firstEntry();
        if (best == null) {
            return Optional.empty();
        }

        BigDecimal totalQuantity = best.getValue().values().stream()
                .map(order -> order.remainingQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return Optional.of(new BookPriceLevel(best.getKey(), totalQuantity, best.getValue().size()));
    }

    private String nextTradeId() {
        return symbol + "-trade-" + nextTradeNumber++;
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static class RestingOrder {
        private final String orderId;
        private final Side side;
        private final BigDecimal price;
        private BigDecimal remainingQuantity;

        private RestingOrder(String orderId, Side side, BigDecimal price, BigDecimal remainingQuantity) {
            this.orderId = orderId;
            this.side = side;
            this.price = price;
            this.remainingQuantity = remainingQuantity;
        }
    }
}
