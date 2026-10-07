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

class OrderCommandProcessorTest {
    private static final Instant NOW = Instant.parse("2026-01-01T09:30:00Z");

    @Test
    void acceptsOrderAndEmitsBothSidesOfEachExecution() {
        var processor = new OrderCommandProcessor();
        processor.handle(new NewOrderCommand(limit("ask-1", Side.SELL, "3", "10.50"), NOW));
        processor.handle(new NewOrderCommand(limit("ask-2", Side.SELL, "4", "10.60"), NOW.plusSeconds(1)));

        List<OrderEvent> events = processor.handle(
                new NewOrderCommand(limit("buy-1", Side.BUY, "6", "10.60"), NOW.plusSeconds(2)));
        Order buyer = processor.findOrder("buy-1");

        assertAll(
                () -> assertInstanceOf(OrderAccepted.class, events.getFirst()),
                () -> assertEquals(5, events.size()),
                () -> assertEquals(4, events.stream().filter(ExecutionReport.class::isInstance).count()),
                () -> assertEquals("6", buyer.getFilledQuantity().toPlainString()),
                () -> assertEquals("10.55", buyer.getAverageFillPrice().toPlainString()),
                () -> assertEquals(OrderStatus.FILLED, buyer.getStatus()),
                () -> assertEquals(OrderStatus.PARTIALLY_FILLED, processor.findOrder("ask-2").getStatus()));
    }

    @Test
    void marketRemainderIsCancelledAfterFillsHaveUpdatedOrderState() {
        var processor = new OrderCommandProcessor();
        processor.handle(new NewOrderCommand(limit("ask-1", Side.SELL, "2", "10.50"), NOW));

        List<OrderEvent> events = processor.handle(
                new NewOrderCommand(market("buy-market", Side.BUY, "5"), NOW.plusSeconds(1)));
        Order marketOrder = processor.findOrder("buy-market");
        OrderCancelled cancelled = events.stream().filter(OrderCancelled.class::isInstance)
                .map(OrderCancelled.class::cast).findFirst().orElseThrow();

        assertAll(
                () -> assertEquals(OrderStatus.CANCELLED, marketOrder.getStatus()),
                () -> assertEquals("2", marketOrder.getFilledQuantity().toPlainString()),
                () -> assertEquals("3", cancelled.cancelledQuantity().toPlainString()),
                () -> assertEquals("MARKET_REMAINDER", cancelled.reason()));
    }

    @Test
    void clientCancelHasASeparateSuccessOrRejectEvent() {
        var processor = new OrderCommandProcessor();
        processor.handle(new NewOrderCommand(limit("resting", Side.BUY, "5", "10.40"), NOW));

        List<OrderEvent> cancelled = processor.handle(new CancelOrderCommand(
                "resting", "cancel-1", NOW.plusSeconds(1)));
        List<OrderEvent> rejected = processor.handle(new CancelOrderCommand(
                "resting", "cancel-2", NOW.plusSeconds(2)));

        assertAll(
                () -> assertInstanceOf(OrderCancelled.class, cancelled.getFirst()),
                () -> assertEquals("5", ((OrderCancelled) cancelled.getFirst()).cancelledQuantity().toPlainString()),
                () -> assertInstanceOf(CancelRejected.class, rejected.getFirst()),
                () -> assertEquals(OrderStatus.CANCELLED, processor.findOrder("resting").getStatus()));
    }

    @Test
    void duplicateOrderIdAndUnknownOrderAreRejected() {
        var processor = new OrderCommandProcessor();
        processor.handle(new NewOrderCommand(limit("same", Side.BUY, "1", "10.00"), NOW));

        List<OrderEvent> duplicate = processor.handle(
                new NewOrderCommand(limit("same", Side.BUY, "1", "10.00"), NOW.plusSeconds(1)));
        List<OrderEvent> unknownCancel = processor.handle(new CancelOrderCommand(
                "missing", "cancel-missing", NOW.plusSeconds(2)));

        assertAll(
                () -> assertInstanceOf(OrderRejected.class, duplicate.getFirst()),
                () -> assertInstanceOf(CancelRejected.class, unknownCancel.getFirst()));
    }

    private static Order limit(String id, Side side, String quantity, String price) {
        return new Order(id, "client-1", "request-" + id, "DEMO", side,
                new BigDecimal(quantity), OrderType.LIMIT, new BigDecimal(price), TimeInForce.DAY, NOW);
    }

    private static Order market(String id, Side side, String quantity) {
        return new Order(id, "client-1", "request-" + id, "DEMO", side,
                new BigDecimal(quantity), OrderType.MARKET, null, TimeInForce.DAY, NOW);
    }
}
