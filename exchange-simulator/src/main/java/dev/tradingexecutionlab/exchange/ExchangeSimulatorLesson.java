package dev.tradingexecutionlab.exchange;

import dev.tradingexecutionlab.domain.Execution;
import dev.tradingexecutionlab.domain.Order;
import dev.tradingexecutionlab.domain.OrderType;
import dev.tradingexecutionlab.domain.Side;
import dev.tradingexecutionlab.domain.TimeInForce;

import java.math.BigDecimal;
import java.time.Instant;

/** Demonstrates a partial fill whose quantity comes from displayed market liquidity. */
public class ExchangeSimulatorLesson {
    public static void main(String[] args) {
        Instant now = Instant.parse("2026-01-01T09:30:00Z");
        Order order = new Order("order-001", "client-demo", "request-001", "DEMO", Side.BUY,
                new BigDecimal("100"), OrderType.LIMIT, new BigDecimal("10.60"),
                TimeInForce.DAY, now);
        order.acknowledge();

        MarketSnapshot market = new MarketSnapshot("DEMO",
                new BigDecimal("10.40"), new BigDecimal("80"),
                new BigDecimal("10.50"), new BigDecimal("40"), now);

        Execution execution = new ExchangeSimulator()
                .simulateFill(order, market, "fill-001", now.plusSeconds(1))
                .orElseThrow();
        order.applyExecution(execution);

        System.out.printf("execution=%s, status=%s, filled=%s/%s, remaining=%s, averagePrice=%s%n",
                execution.executionId(), order.getStatus(), order.getFilledQuantity(),
                order.getQuantity(), order.remainingQuantity(), order.getAverageFillPrice());
    }
}
