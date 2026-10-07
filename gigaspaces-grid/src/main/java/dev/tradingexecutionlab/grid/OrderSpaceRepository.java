package dev.tradingexecutionlab.grid;

import com.gigaspaces.client.ChangeSet;
import com.gigaspaces.query.IdQuery;
import com.j_spaces.core.client.SQLQuery;
import dev.tradingexecutionlab.domain.Order;
import org.openspaces.core.GigaSpace;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Basic Space operations for the single-writer lesson. */
public class OrderSpaceRepository {
    private final GigaSpace space;

    public OrderSpaceRepository(GigaSpace space) {
        this.space = Objects.requireNonNull(space, "space");
    }

    /** Default write creates a new entry or replaces the snapshot with the same ID. */
    public void save(Order order) {
        space.write(OrderEntry.from(order));
    }

    public Optional<OrderEntry> findById(String orderId) {
        return Optional.ofNullable(space.read(new IdQuery<>(OrderEntry.class, orderId, orderId)));
    }

    public List<OrderEntry> findBySymbol(String symbol) {
        SQLQuery<OrderEntry> query = new SQLQuery<>(OrderEntry.class, "symbol = ?");
        query.setParameter(1, symbol);
        return Arrays.asList(space.readMultiple(query));
    }

    /** Applies the state already calculated by the domain object, in one Space change call. */
    public void updateState(Order order) {
        ChangeSet changes = new ChangeSet()
                .set("status", order.getStatus())
                .set("filledQuantity", order.getFilledQuantity())
                .set("averageFillPrice", order.getAverageFillPrice())
                .set("rejectionReason", order.getRejectionReason());
        var result = space.change(new IdQuery<>(OrderEntry.class, order.getOrderId(), order.getOrderId()), changes);
        if (result.getNumberOfChangedEntries() == 0) {
            throw new IllegalArgumentException("Order is not stored in the Space: " + order.getOrderId());
        }
    }

    /** Reads and removes the entry. This is a Space operation, not a business cancellation. */
    public Optional<OrderEntry> takeById(String orderId) {
        return Optional.ofNullable(space.take(new IdQuery<>(OrderEntry.class, orderId, orderId)));
    }
}
