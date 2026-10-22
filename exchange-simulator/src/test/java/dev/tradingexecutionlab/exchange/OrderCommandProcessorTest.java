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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
        processor.handle(new NewOrderCommand(limitWithRequest("same", "request-first",
                Side.BUY, "1", "10.00"), NOW));

        List<OrderEvent> duplicate = processor.handle(
                new NewOrderCommand(limitWithRequest("same", "request-second",
                        Side.BUY, "1", "10.00"), NOW.plusSeconds(1)));
        List<OrderEvent> unknownCancel = processor.handle(new CancelOrderCommand(
                "missing", "cancel-missing", NOW.plusSeconds(2)));

        assertAll(
                () -> assertInstanceOf(OrderRejected.class, duplicate.getFirst()),
                () -> assertInstanceOf(CancelRejected.class, unknownCancel.getFirst()));
    }

    @Test
    void retryingNewOrderReplaysOriginalEventsWithoutMatchingTwice() {
        var processor = new OrderCommandProcessor();
        processor.handle(new NewOrderCommand(limit("ask", Side.SELL, "5", "10.50"), NOW));
        Order buy = limit("buy", Side.BUY, "3", "10.50");
        NewOrderCommand firstCommand = new NewOrderCommand(buy, NOW.plusSeconds(1));

        List<OrderEvent> first = processor.handle(firstCommand);
        List<OrderEvent> retry = processor.handle(new NewOrderCommand(buy, NOW.plusSeconds(2)));

        assertAll(
                () -> assertEquals(first, retry),
                () -> assertEquals("3", processor.findOrder("buy").getFilledQuantity().toPlainString()),
                () -> assertEquals("2", processor.findOrder("ask").remainingQuantity().toPlainString()));
    }

    @Test
    void reusingClientRequestIdWithDifferentTermsIsRejectedWithoutChangingOriginalOrder() {
        var processor = new OrderCommandProcessor();
        processor.handle(new NewOrderCommand(limitWithRequest("order-1", "client-request-1",
                Side.BUY, "2", "10.50"), NOW));

        List<OrderEvent> conflict = processor.handle(new NewOrderCommand(limitWithRequest("order-2",
                "client-request-1", Side.BUY, "3", "10.50"), NOW.plusSeconds(1)));

        assertAll(
                () -> assertInstanceOf(OrderRejected.class, conflict.getFirst()),
                () -> assertEquals("2", processor.findOrder("order-1").getQuantity().toPlainString()),
                () -> assertNull(processor.findOrder("order-2")));
    }

    @Test
    void duplicateCancelRequestReplaysTheOriginalConfirmation() {
        var processor = new OrderCommandProcessor();
        processor.handle(new NewOrderCommand(limit("resting", Side.BUY, "5", "10.40"), NOW));

        CancelOrderCommand cancel = new CancelOrderCommand("resting", "cancel-1", NOW.plusSeconds(1));
        List<OrderEvent> first = processor.handle(cancel);
        List<OrderEvent> retry = processor.handle(new CancelOrderCommand(
                "resting", "cancel-1", NOW.plusSeconds(2)));

        assertAll(
                () -> assertEquals(first, retry),
                () -> assertInstanceOf(OrderCancelled.class, retry.getFirst()),
                () -> assertEquals(OrderStatus.CANCELLED, processor.findOrder("resting").getStatus()));
    }

    @Test
    void concurrentRetriesOfTheSameNewOrderAreProcessedOnce() throws Exception {
        var processor = new OrderCommandProcessor();
        var order = limit("concurrent", Side.BUY, "1", "10.00");
        var command = new NewOrderCommand(order, NOW);
        int callerCount = 8;
        var ready = new CountDownLatch(callerCount);
        var start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(callerCount)) {
            List<Future<List<OrderEvent>>> results = new java.util.ArrayList<>();
            for (int i = 0; i < callerCount; i++) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return processor.handle(command);
                }));
            }
            ready.await();
            start.countDown();
            List<OrderEvent> expected = results.getFirst().get();

            assertAll(
                    () -> assertTrue(results.stream().allMatch(future -> get(future).equals(expected))),
                    () -> assertEquals(OrderStatus.ACKNOWLEDGED, processor.findOrder("concurrent").getStatus()),
                    () -> assertEquals(1, expected.stream().filter(OrderAccepted.class::isInstance).count()));
        }
    }

    private static Order limit(String id, Side side, String quantity, String price) {
        return limitWithRequest(id, "request-" + id, side, quantity, price);
    }

    private static Order limitWithRequest(String id, String clientOrderId, Side side,
                                          String quantity, String price) {
        return new Order(id, "client-1", clientOrderId, "DEMO", side,
                new BigDecimal(quantity), OrderType.LIMIT, new BigDecimal(price), TimeInForce.DAY, NOW);
    }

    private static Order market(String id, Side side, String quantity) {
        return new Order(id, "client-1", "request-" + id, "DEMO", side,
                new BigDecimal(quantity), OrderType.MARKET, null, TimeInForce.DAY, NOW);
    }

    private static List<OrderEvent> get(Future<List<OrderEvent>> future) {
        try {
            return future.get();
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}
