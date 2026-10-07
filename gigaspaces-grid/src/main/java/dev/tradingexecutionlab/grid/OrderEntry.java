package dev.tradingexecutionlab.grid;

import com.gigaspaces.annotation.pojo.SpaceClass;
import com.gigaspaces.annotation.pojo.SpaceId;
import com.gigaspaces.annotation.pojo.SpaceIndex;
import com.gigaspaces.annotation.pojo.SpaceRouting;
import dev.tradingexecutionlab.domain.Order;
import dev.tradingexecutionlab.domain.OrderStatus;
import dev.tradingexecutionlab.domain.Side;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/** A Space data object containing a snapshot of an order. */
@SpaceClass
@Getter
@Setter
@NoArgsConstructor
public class OrderEntry {
    private String orderId;
    private String clientId;
    private String symbol;
    private Side side;
    private BigDecimal quantity;
    private Instant createdAt;
    private OrderStatus status;
    private BigDecimal filledQuantity;
    private BigDecimal averageFillPrice;
    private String rejectionReason;

    @SpaceId(autoGenerate = false)
    @SpaceRouting
    public String getOrderId() {
        return orderId;
    }

    @SpaceIndex
    public String getSymbol() {
        return symbol;
    }

    public static OrderEntry from(Order order) {
        OrderEntry entry = new OrderEntry();
        entry.setOrderId(order.getOrderId());
        entry.setClientId(order.getClientId());
        entry.setSymbol(order.getSymbol());
        entry.setSide(order.getSide());
        entry.setQuantity(order.getQuantity());
        entry.setCreatedAt(order.getCreatedAt());
        entry.setStatus(order.getStatus());
        entry.setFilledQuantity(order.getFilledQuantity());
        entry.setAverageFillPrice(order.getAverageFillPrice());
        entry.setRejectionReason(order.getRejectionReason());
        return entry;
    }
}
