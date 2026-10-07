package dev.tradingexecutionlab.exchange;

import dev.tradingexecutionlab.domain.Order;
import dev.tradingexecutionlab.domain.OrderStatus;
import dev.tradingexecutionlab.domain.OrderType;
import dev.tradingexecutionlab.domain.Side;
import dev.tradingexecutionlab.domain.TimeInForce;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OrderBookTest {
    private static final Instant NOW = Instant.parse("2026-01-01T09:30:00Z");

    @Test
    void bestBidAndAskUsePricePriorityAndCombineSamePriceSize() {
        OrderBook book = new OrderBook("DEMO");
        book.submit(limit("bid-low", Side.BUY, "2", "10.40"), NOW);
        book.submit(limit("bid-high-1", Side.BUY, "3", "10.50"), NOW);
        book.submit(limit("bid-high-2", Side.BUY, "4", "10.500"), NOW);
        book.submit(limit("ask-high", Side.SELL, "2", "10.70"), NOW);
        book.submit(limit("ask-low", Side.SELL, "5", "10.60"), NOW);

        assertAll(
                () -> assertEquals("10.50", book.bestBid().orElseThrow().price().toPlainString()),
                () -> assertEquals("7", book.bestBid().orElseThrow().totalQuantity().toPlainString()),
                () -> assertEquals(2, book.bestBid().orElseThrow().orderCount()),
                () -> assertEquals("10.60", book.bestAsk().orElseThrow().price().toPlainString()));
    }

    @Test
    void nonCrossingLimitOrderRestsOnItsSide() {
        OrderBook book = new OrderBook("DEMO");
        book.submit(limit("ask-1", Side.SELL, "5", "10.50"), NOW);

        MatchResult result = book.submit(limit("bid-1", Side.BUY, "3", "10.40"), NOW);

        assertAll(
                () -> assertTrue(result.trades().isEmpty()),
                () -> assertEquals("3", result.restingQuantity().toPlainString()),
                () -> assertEquals("10.40", book.bestBid().orElseThrow().price().toPlainString()),
                () -> assertEquals("10.50", book.bestAsk().orElseThrow().price().toPlainString()));
    }

    @Test
    void buySweepsBestAskFirstAndRestsLimitRemainder() {
        OrderBook book = new OrderBook("DEMO");
        book.submit(limit("ask-high", Side.SELL, "3", "10.60"), NOW);
        book.submit(limit("ask-low", Side.SELL, "4", "10.50"), NOW.plusSeconds(1));

        MatchResult result = book.submit(limit("buy-1", Side.BUY, "9", "10.60"), NOW.plusSeconds(2));

        assertAll(
                () -> assertEquals(List.of("ask-low", "ask-high"),
                        result.trades().stream().map(Trade::sellOrderId).toList()),
                () -> assertEquals(List.of("10.50", "10.60"),
                        result.trades().stream().map(trade -> trade.price().toPlainString()).toList()),
                () -> assertEquals("7", result.filledQuantity().toPlainString()),
                () -> assertEquals("2", result.remainingQuantity().toPlainString()),
                () -> assertEquals("2", result.restingQuantity().toPlainString()),
                () -> assertTrue(book.bestAsk().isEmpty()),
                () -> assertEquals("10.60", book.bestBid().orElseThrow().price().toPlainString()),
                () -> assertEquals("2", book.bestBid().orElseThrow().totalQuantity().toPlainString()));
    }

    @Test
    void samePriceOrdersMatchInFifoOrder() {
        OrderBook book = new OrderBook("DEMO");
        book.submit(limit("ask-first", Side.SELL, "5", "10.50"), NOW);
        book.submit(limit("ask-second", Side.SELL, "5", "10.50"), NOW.plusSeconds(1));

        MatchResult result = book.submit(limit("buy-1", Side.BUY, "6", "10.50"), NOW.plusSeconds(2));

        assertAll(
                () -> assertEquals(List.of("ask-first", "ask-second"),
                        result.trades().stream().map(Trade::sellOrderId).toList()),
                () -> assertEquals(List.of("5", "1"),
                        result.trades().stream().map(trade -> trade.quantity().toPlainString()).toList()),
                () -> assertEquals("4", book.bestAsk().orElseThrow().totalQuantity().toPlainString()),
                () -> assertEquals(1, book.bestAsk().orElseThrow().orderCount()));
    }

    @Test
    void partiallyFilledRestingOrderKeepsItsTimePriority() {
        OrderBook book = new OrderBook("DEMO");
        book.submit(limit("ask-first", Side.SELL, "5", "10.50"), NOW);
        book.submit(limit("ask-second", Side.SELL, "5", "10.50"), NOW.plusSeconds(1));
        book.submit(limit("buy-partial", Side.BUY, "2", "10.50"), NOW.plusSeconds(2));

        MatchResult result = book.submit(limit("buy-next", Side.BUY, "4", "10.50"), NOW.plusSeconds(3));

        assertAll(
                () -> assertEquals(List.of("ask-first", "ask-second"),
                        result.trades().stream().map(Trade::sellOrderId).toList()),
                () -> assertEquals(List.of("3", "1"),
                        result.trades().stream().map(trade -> trade.quantity().toPlainString()).toList()),
                () -> assertEquals("4", book.bestAsk().orElseThrow().totalQuantity().toPlainString()),
                () -> assertEquals(1, book.bestAsk().orElseThrow().orderCount()));
    }

    @Test
    void sellSweepsHighestBidFirstAtRestingPrices() {
        OrderBook book = new OrderBook("DEMO");
        book.submit(limit("bid-low", Side.BUY, "3", "10.40"), NOW);
        book.submit(limit("bid-high", Side.BUY, "2", "10.50"), NOW.plusSeconds(1));

        MatchResult result = book.submit(limit("sell-1", Side.SELL, "4", "10.40"), NOW.plusSeconds(2));

        assertAll(
                () -> assertEquals(List.of("10.50", "10.40"),
                        result.trades().stream().map(trade -> trade.price().toPlainString()).toList()),
                () -> assertEquals(List.of("bid-high", "bid-low"),
                        result.trades().stream().map(Trade::buyOrderId).toList()),
                () -> assertEquals("1", book.bestBid().orElseThrow().totalQuantity().toPlainString()));
    }

    @Test
    void marketOrderCancelsRemainderInsteadOfResting() {
        OrderBook book = new OrderBook("DEMO");
        book.submit(limit("ask-1", Side.SELL, "4", "10.50"), NOW);

        MatchResult result = book.submit(market("buy-market", Side.BUY, "7"), NOW.plusSeconds(1));

        assertAll(
                () -> assertEquals("4", result.filledQuantity().toPlainString()),
                () -> assertEquals("3", result.remainingQuantity().toPlainString()),
                () -> assertEquals("0", result.restingQuantity().toPlainString()),
                () -> assertEquals("3", result.cancelledQuantity().toPlainString()),
                () -> assertTrue(book.bestBid().isEmpty()));
    }

    @Test
    void marketOrderWithNoLiquidityCancelsItsWholeQuantity() {
        OrderBook book = new OrderBook("DEMO");

        MatchResult result = book.submit(market("buy-market", Side.BUY, "7"), NOW);

        assertEquals("7", result.cancelledQuantity().toPlainString());
        assertTrue(result.trades().isEmpty());
        assertTrue(book.bestBid().isEmpty());
    }

    @Test
    void cancelRemovesOnlyThatOrderAndRemovesAnEmptyPriceLevel() {
        OrderBook book = new OrderBook("DEMO");
        book.submit(limit("ask-first", Side.SELL, "3", "10.50"), NOW);
        book.submit(limit("ask-second", Side.SELL, "4", "10.50"), NOW.plusSeconds(1));

        CancelResult result = book.cancel("ask-first").orElseThrow();

        assertAll(
                () -> assertEquals("3", result.cancelledQuantity().toPlainString()),
                () -> assertEquals("10.50", book.bestAsk().orElseThrow().price().toPlainString()),
                () -> assertEquals("4", book.bestAsk().orElseThrow().totalQuantity().toPlainString()),
                () -> assertTrue(book.cancel("ask-first").isEmpty()));

        assertEquals("4", book.cancel("ask-second").orElseThrow().cancelledQuantity().toPlainString());
        assertTrue(book.bestAsk().isEmpty());
    }

    @Test
    void rejectsWrongSymbolUnacknowledgedAndDuplicateOrder() {
        OrderBook book = new OrderBook("DEMO");
        Order wrongSymbol = limit("wrong-symbol", Side.BUY, "1", "10.00", "OTHER");
        Order notAcknowledged = new Order("new-order", "client", "request-new", "DEMO", Side.BUY,
                new BigDecimal("1"), OrderType.LIMIT, new BigDecimal("10.00"), TimeInForce.DAY, NOW);

        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> book.submit(wrongSymbol, NOW)),
                () -> assertThrows(IllegalStateException.class, () -> book.submit(notAcknowledged, NOW)));

        Order accepted = limit("same-id", Side.BUY, "1", "10.00");
        book.submit(accepted, NOW);
        assertThrows(IllegalArgumentException.class, () -> book.submit(accepted, NOW));
    }

    private static Order limit(String id, Side side, String quantity, String price) {
        return limit(id, side, quantity, price, "DEMO");
    }

    private static Order limit(String id, Side side, String quantity, String price, String symbol) {
        Order order = new Order(id, "client-1", "request-" + id, symbol, side,
                new BigDecimal(quantity), OrderType.LIMIT, new BigDecimal(price), TimeInForce.DAY, NOW);
        order.acknowledge();
        return order;
    }

    private static Order market(String id, Side side, String quantity) {
        Order order = new Order(id, "client-1", "request-" + id, "DEMO", side,
                new BigDecimal(quantity), OrderType.MARKET, null, TimeInForce.DAY, NOW);
        order.acknowledge();
        return order;
    }
}
