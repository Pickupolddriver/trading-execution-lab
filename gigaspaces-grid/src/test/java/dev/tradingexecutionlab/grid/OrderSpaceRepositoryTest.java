package dev.tradingexecutionlab.grid;

import dev.tradingexecutionlab.domain.Execution;
import dev.tradingexecutionlab.domain.Order;
import dev.tradingexecutionlab.domain.OrderStatus;
import dev.tradingexecutionlab.domain.Side;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class OrderSpaceRepositoryTest {
    private static LocalOrderSpace localSpace;
    private static OrderSpaceRepository repository;

    @BeforeAll
    static void startSpace() {
        localSpace = new LocalOrderSpace("orders-test");
        repository = new OrderSpaceRepository(localSpace.getGigaSpace());
    }

    @AfterAll
    static void closeSpace() {
        if (localSpace != null) {
            localSpace.close();
        }
    }

    @BeforeEach
    void clearOrders() {
        localSpace.getGigaSpace().clear(new OrderEntry());
    }

    @Test
    void writeAndReadPreserveTheOrderSnapshot() {
        Order order = order("order-1", "DEMO");
        repository.save(order);
        OrderEntry stored = repository.findById("order-1").orElseThrow();
        assertAll(
                () -> assertEquals(order.getClientId(), stored.getClientId()),
                () -> assertEquals(order.getSide(), stored.getSide()),
                () -> assertEquals(0, order.getQuantity().compareTo(stored.getQuantity())),
                () -> assertEquals(order.getCreatedAt(), stored.getCreatedAt()),
                () -> assertEquals(OrderStatus.NEW, stored.getStatus()));
    }

    @Test
    void changingAReadResultDoesNotChangeTheStoredEntry() {
        repository.save(order("order-1", "DEMO"));
        OrderEntry readCopy = repository.findById("order-1").orElseThrow();
        readCopy.setStatus(OrderStatus.CANCELLED);
        assertEquals(OrderStatus.NEW, repository.findById("order-1").orElseThrow().getStatus());
    }

    @Test
    void writingTheSameIdUpdatesRatherThanAddsAnEntry() {
        Order order = order("order-1", "DEMO");
        repository.save(order);
        order.acknowledge();
        repository.save(order);
        assertEquals(1, repository.findBySymbol("DEMO").size());
        assertEquals(OrderStatus.ACKNOWLEDGED, repository.findById("order-1").orElseThrow().getStatus());
    }

    @Test
    void changeStoresAValidatedFillAndPreservesOrderTerms() {
        Order order = order("order-1", "DEMO");
        order.acknowledge();
        repository.save(order);
        order.applyExecution(new Execution("fill-1", "order-1", new BigDecimal("40"),
                new BigDecimal("10.50"), Instant.parse("2026-01-01T00:00:01Z")));
        repository.updateState(order);
        OrderEntry stored = repository.findById("order-1").orElseThrow();
        assertAll(
                () -> assertEquals(OrderStatus.PARTIALLY_FILLED, stored.getStatus()),
                () -> assertEquals(0, stored.getFilledQuantity().compareTo(new BigDecimal("40"))),
                () -> assertEquals(0, stored.getAverageFillPrice().compareTo(new BigDecimal("10.50"))),
                () -> assertEquals(0, order.getQuantity().compareTo(stored.getQuantity())),
                () -> assertEquals(order.getSymbol(), stored.getSymbol()));
    }

    @Test
    void queryFiltersBySymbolAndTakeRemovesOnlyTheSelectedOrder() {
        repository.save(order("order-1", "DEMO"));
        repository.save(order("order-2", "OTHER"));
        assertEquals(1, repository.findBySymbol("DEMO").size());
        assertEquals("order-1", repository.takeById("order-1").orElseThrow().getOrderId());
        assertTrue(repository.findById("order-1").isEmpty());
        assertTrue(repository.findById("order-2").isPresent());
        assertTrue(repository.takeById("missing").isEmpty());
    }

    @Test
    void changingAMissingOrderDoesNotInsertOne() {
        assertThrows(IllegalArgumentException.class,
                () -> repository.updateState(order("missing", "DEMO")));
        assertTrue(repository.findById("missing").isEmpty());
    }

    private static Order order(String id, String symbol) {
        return new Order(id, "client-demo", symbol, Side.BUY, new BigDecimal("100"),
                Instant.parse("2026-01-01T00:00:00Z"));
    }
}
