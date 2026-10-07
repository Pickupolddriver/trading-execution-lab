package dev.tradingexecutionlab.exchange;

import dev.tradingexecutionlab.domain.Execution;
import dev.tradingexecutionlab.domain.OrderStatus;
import dev.tradingexecutionlab.domain.Side;

import java.math.BigDecimal;
import java.time.Instant;

/** One side's immutable view of a trade, before any FIX wire mapping. */
public record ExecutionReport(
        String orderId,
        String counterOrderId,
        String tradeId,
        Side side,
        Execution execution,
        BigDecimal cumulativeQuantity,
        BigDecimal leavesQuantity,
        OrderStatus orderStatus,
        Instant eventTime) implements OrderEvent {
}
