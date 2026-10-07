package dev.tradingexecutionlab.grid;

import dev.tradingexecutionlab.domain.Execution;
import dev.tradingexecutionlab.domain.Order;
import dev.tradingexecutionlab.domain.Side;

import java.math.BigDecimal;
import java.time.Instant;

/** Run with Maven exec:exec to follow one order through write, read, change and take. */
public class SpaceLesson {
    public static void main(String[] args) {
        try (LocalOrderSpace localSpace = new LocalOrderSpace("orders-lesson")) {
            OrderSpaceRepository repository = new OrderSpaceRepository(localSpace.getGigaSpace());
            Order order = new Order("order-001", "client-demo", "DEMO", Side.BUY,
                    new BigDecimal("100"), Instant.parse("2026-01-01T00:00:00Z"));

            repository.save(order);
            print("write + read", repository.findById(order.getOrderId()).orElseThrow());

            order.acknowledge();
            repository.save(order);
            print("write same ID", repository.findById(order.getOrderId()).orElseThrow());

            order.applyExecution(new Execution("fill-001", order.getOrderId(),
                    new BigDecimal("40"), new BigDecimal("10.50"),
                    Instant.parse("2026-01-01T00:00:01Z")));
            repository.updateState(order);
            print("change", repository.findById(order.getOrderId()).orElseThrow());
            System.out.println("query DEMO: " + repository.findBySymbol("DEMO").size() + " order(s)");

            print("take", repository.takeById(order.getOrderId()).orElseThrow());
            System.out.println("read after take: " + repository.findById(order.getOrderId()));
        }
    }

    private static void print(String operation, OrderEntry entry) {
        System.out.printf("%s: %s, status=%s, filled=%s/%s, averagePrice=%s%n",
                operation, entry.getOrderId(), entry.getStatus(), entry.getFilledQuantity(),
                entry.getQuantity(), entry.getAverageFillPrice());
    }
}
