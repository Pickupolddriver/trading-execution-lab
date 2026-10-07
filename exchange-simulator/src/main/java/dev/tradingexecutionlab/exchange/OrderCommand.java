package dev.tradingexecutionlab.exchange;

/** A synchronous command accepted by the lesson's order processor. */
public sealed interface OrderCommand permits NewOrderCommand, CancelOrderCommand {
}
