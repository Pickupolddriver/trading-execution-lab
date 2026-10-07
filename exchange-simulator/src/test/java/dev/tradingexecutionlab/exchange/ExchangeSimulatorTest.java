package dev.tradingexecutionlab.exchange;

import dev.tradingexecutionlab.domain.Order;
import dev.tradingexecutionlab.domain.OrderStatus;
import dev.tradingexecutionlab.domain.OrderType;
import dev.tradingexecutionlab.domain.Execution;
import dev.tradingexecutionlab.domain.Side;
import dev.tradingexecutionlab.domain.TimeInForce;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class ExchangeSimulatorTest {
    private static final Instant NOW = Instant.parse("2026-01-01T09:30:00Z");
    private final ExchangeSimulator simulator = new ExchangeSimulator();

    @Test
    void buyLimitFillsAtAskUpToDisplayedSize() {
        Order order = order(Side.BUY, OrderType.LIMIT, "100", "10.60");
        MarketSnapshot market = market("10.40", "80", "10.50", "40");

        var fill = simulator.simulateFill(order, market, "fill-1", NOW.plusSeconds(1)).orElseThrow();

        assertAll(
                () -> assertEquals("40", fill.quantity().toPlainString()),
                () -> assertEquals("10.50", fill.price().toPlainString()),
                () -> assertEquals(order.getOrderId(), fill.orderId()),
                () -> assertEquals(OrderStatus.ACKNOWLEDGED, order.getStatus()),
                () -> assertEquals("0", order.getFilledQuantity().toPlainString()));
    }

    @Test
    void buyLimitDoesNotFillAboveItsLimit() {
        Order order = order(Side.BUY, OrderType.LIMIT, "100", "10.40");

        assertTrue(simulator.simulateFill(order, market("10.30", "80", "10.50", "40"),
                "fill-1", NOW).isEmpty());
    }

    @Test
    void sellLimitFillsAtBidWhenBidMeetsLimit() {
        Order order = order(Side.SELL, OrderType.LIMIT, "100", "10.50");

        var fill = simulator.simulateFill(order, market("10.60", "25", "10.70", "80"),
                "fill-1", NOW).orElseThrow();

        assertEquals("25", fill.quantity().toPlainString());
        assertEquals("10.60", fill.price().toPlainString());
    }

    @Test
    void sellLimitDoesNotFillBelowItsLimit() {
        Order order = order(Side.SELL, OrderType.LIMIT, "100", "10.60");

        assertTrue(simulator.simulateFill(order, market("10.50", "25", "10.70", "80"),
                "fill-1", NOW).isEmpty());
    }

    @Test
    void marketOrderAlsoNeedsDisplayedLiquidity() {
        Order order = order(Side.BUY, OrderType.MARKET, "100", null);

        assertTrue(simulator.simulateFill(order, market("10.40", "80", "10.50", "0"),
                "fill-1", NOW).isEmpty());
    }

    @Test
    void fillIsCappedAtRemainingQuantity() {
        Order order = order(Side.BUY, OrderType.MARKET, "100", null);
        order.applyExecution(new Execution(
                "prior-fill", order.getOrderId(), new BigDecimal("70"), new BigDecimal("10.40"), NOW));

        var fill = simulator.simulateFill(order, market("10.30", "80", "10.50", "60"),
                "fill-2", NOW.plusSeconds(1)).orElseThrow();

        assertEquals("30", fill.quantity().toPlainString());
    }

    @Test
    void rejectsUnacknowledgedOrTerminalOrder() {
        Order newOrder = new Order("new-order", "client-1", "request-new", "DEMO", Side.BUY,
                new BigDecimal("100"), OrderType.MARKET, null, TimeInForce.DAY, NOW);
        Order filledOrder = order(Side.BUY, OrderType.MARKET, "100", null);
        filledOrder.applyExecution(new Execution(
                "fill", filledOrder.getOrderId(), new BigDecimal("100"), new BigDecimal("10.50"), NOW));

        assertAll(
                () -> assertThrows(IllegalStateException.class,
                        () -> simulator.simulateFill(newOrder, market("10.40", "80", "10.50", "40"),
                                "fill-1", NOW)),
                () -> assertThrows(IllegalStateException.class,
                        () -> simulator.simulateFill(filledOrder, market("10.40", "80", "10.50", "40"),
                                "fill-2", NOW)));
    }

    @Test
    void rejectsSnapshotForAnotherSymbol() {
        Order order = order(Side.BUY, OrderType.MARKET, "100", null);

        assertThrows(IllegalArgumentException.class,
                () -> simulator.simulateFill(order, new MarketSnapshot("OTHER",
                                new BigDecimal("10.40"), new BigDecimal("80"),
                                new BigDecimal("10.50"), new BigDecimal("40"), NOW),
                        "fill-1", NOW));
    }

    private static Order order(Side side, OrderType type, String quantity, String limitPrice) {
        Order order = new Order("order-1", "client-1", "request-1", "DEMO", side,
                new BigDecimal(quantity), type,
                limitPrice == null ? null : new BigDecimal(limitPrice), TimeInForce.DAY, NOW);
        order.acknowledge();
        return order;
    }

    private static MarketSnapshot market(String bid, String bidSize, String ask, String askSize) {
        return new MarketSnapshot("DEMO", new BigDecimal(bid), new BigDecimal(bidSize),
                new BigDecimal(ask), new BigDecimal(askSize), NOW);
    }
}
