package dev.tradingexecutionlab.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

class OrderConcurrencyTest {
    private static final Instant NOW = Instant.parse("2026-01-01T09:30:00Z");

    @Test
    void optimisticVersionAllowsOnlyOneConcurrentFillFromTheSameSnapshot() throws Exception {
        Order order = order("order-1");
        order.acknowledge();
        long expectedVersion = order.snapshot().version();
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<ExecutionApplyResult> first = executor.submit(() -> applyAfterStart(
                    order, execution("exec-1", "6"), expectedVersion, ready, start));
            Future<ExecutionApplyResult> second = executor.submit(() -> applyAfterStart(
                    order, execution("exec-2", "6"), expectedVersion, ready, start));

            ready.await();
            start.countDown();
            var results = java.util.List.of(first.get(), second.get());

            assertAll(
                    () -> assertEquals(1, results.stream().filter(r -> r == ExecutionApplyResult.APPLIED).count()),
                    () -> assertEquals(1, results.stream().filter(r -> r == ExecutionApplyResult.VERSION_CONFLICT).count()),
                    () -> assertEquals("6", order.snapshot().filledQuantity().toPlainString()),
                    () -> assertEquals(expectedVersion + 1, order.snapshot().version()));
        }
    }

    @Test
    void ordinaryConcurrentExecutionUpdatesAreSerializedWithoutLostQuantity() throws Exception {
        Order order = order("order-1");
        order.acknowledge();
        var start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<?> first = executor.submit(() -> applyAfter(start, order, execution("exec-1", "6")));
            Future<?> second = executor.submit(() -> applyAfter(start, order, execution("exec-2", "4")));
            start.countDown();
            first.get();
            second.get();
        }

        assertAll(
                () -> assertEquals("10", order.snapshot().filledQuantity().toPlainString()),
                () -> assertEquals(OrderStatus.FILLED, order.snapshot().status()),
                () -> assertEquals(3, order.snapshot().version()));
    }

    @Test
    void duplicateExecutionIsIdempotentEvenIfRetryHasAnOldVersion() {
        Order order = order("order-1");
        order.acknowledge();
        long version = order.snapshot().version();
        Execution execution = execution("exec-1", "3");

        assertEquals(ExecutionApplyResult.APPLIED, order.applyExecution(execution, version));
        assertEquals(ExecutionApplyResult.DUPLICATE, order.applyExecution(execution, version));
        assertEquals("3", order.snapshot().filledQuantity().toPlainString());
    }

    @Test
    void duplicateExecutionComparesDecimalValuesRatherThanScale() {
        Order order = order("order-1");
        order.acknowledge();
        Execution original = execution("exec-1", "3");
        order.applyExecution(original);
        Execution sameValuesWithDifferentScale = new Execution("exec-1", "order-1",
                new BigDecimal("3.00"), new BigDecimal("10.500"), original.executedAt());

        assertEquals(ExecutionApplyResult.DUPLICATE,
                order.applyExecution(sameValuesWithDifferentScale, order.snapshot().version() - 1));
        assertEquals("3", order.snapshot().filledQuantity().toPlainString());
    }

    @Test
    void reusingExecutionIdWithDifferentPayloadIsRejected() {
        Order order = order("order-1");
        order.acknowledge();
        order.applyExecution(execution("exec-1", "3"));

        assertThrows(IllegalArgumentException.class,
                () -> order.applyExecution(execution("exec-1", "4")));
        assertEquals("3", order.snapshot().filledQuantity().toPlainString());
    }

    @Test
    void lifecycleMutationsAdvanceVersionButDuplicateDoesNot() {
        Order order = order("order-1");
        assertEquals(0, order.snapshot().version());
        order.acknowledge();
        assertEquals(1, order.snapshot().version());

        Execution fill = execution("exec-1", "2");
        order.applyExecution(fill);
        assertEquals(2, order.snapshot().version());
        order.applyExecution(fill);
        assertEquals(2, order.snapshot().version());
        order.cancel();
        assertEquals(3, order.snapshot().version());
    }

    private static ExecutionApplyResult applyAfterStart(Order order, Execution execution,
                                                          long expectedVersion, CountDownLatch ready,
                                                          CountDownLatch start) throws InterruptedException {
        ready.countDown();
        start.await();
        return order.applyExecution(execution, expectedVersion);
    }

    private static void applyAfter(CountDownLatch start, Order order, Execution execution) {
        try {
            start.await();
            order.applyExecution(execution);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }

    private static Order order(String id) {
        return new Order(id, "client-1", "request-" + id, "DEMO", Side.BUY,
                new BigDecimal("10"), OrderType.LIMIT, new BigDecimal("10.50"), TimeInForce.DAY, NOW);
    }

    private static Execution execution(String executionId, String quantity) {
        return new Execution(executionId, "order-1", new BigDecimal(quantity),
                new BigDecimal("10.50"), NOW.plusSeconds(1));
    }
}
