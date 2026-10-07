package dev.tradingexecutionlab.exchange;

import dev.tradingexecutionlab.domain.Order;

import java.time.Instant;
import java.util.Objects;

public record NewOrderCommand(Order order, Instant receivedAt) implements OrderCommand {
    public NewOrderCommand {
        Objects.requireNonNull(order, "order");
        Objects.requireNonNull(receivedAt, "receivedAt");
    }
}
