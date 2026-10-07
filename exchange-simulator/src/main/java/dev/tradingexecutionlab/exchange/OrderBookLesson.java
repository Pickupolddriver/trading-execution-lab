package dev.tradingexecutionlab.exchange;

import dev.tradingexecutionlab.domain.Order;
import dev.tradingexecutionlab.domain.OrderType;
import dev.tradingexecutionlab.domain.Side;
import dev.tradingexecutionlab.domain.TimeInForce;

import java.math.BigDecimal;
import java.time.Instant;

/** Demonstrates price-time matching across levels and a resting limit remainder. */
public class OrderBookLesson {
    public static void main(String[] args) {
        Instant now = Instant.parse("2026-01-01T09:30:00Z");
        OrderBook orderBook = new OrderBook("DEMO");

        orderBook.submit(limit("sell-1", Side.SELL, "4", "10.50", now), now);
        orderBook.submit(limit("sell-2", Side.SELL, "3", "10.60", now.plusSeconds(1)), now);

        MatchResult result = orderBook.submit(
                limit("buy-1", Side.BUY, "9", "10.60", now.plusSeconds(2)), now.plusSeconds(2));

        for (Trade trade : result.trades()) {
            System.out.printf("trade=%s, buy=%s, sell=%s, quantity=%s, price=%s%n",
                    trade.tradeId(), trade.buyOrderId(), trade.sellOrderId(),
                    trade.quantity(), trade.price());
        }
        System.out.printf("buy filled=%s, remaining=%s, resting=%s%n",
                result.filledQuantity(), result.remainingQuantity(), result.restingQuantity());
        orderBook.bestBid().ifPresent(level -> System.out.printf(
                "best bid=%s, quantity=%s, orders=%d%n",
                level.price(), level.totalQuantity(), level.orderCount()));
    }

    private static Order limit(String id, Side side, String quantity, String price, Instant createdAt) {
        Order order = new Order(id, "client-demo", "request-" + id, "DEMO", side,
                new BigDecimal(quantity), OrderType.LIMIT, new BigDecimal(price),
                TimeInForce.DAY, createdAt);
        order.acknowledge();
        return order;
    }
}
