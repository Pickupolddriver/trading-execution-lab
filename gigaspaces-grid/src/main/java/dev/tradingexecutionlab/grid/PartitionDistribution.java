package dev.tradingexecutionlab.grid;

import java.util.List;

/** Immutable summary of synthetic routing placement. */
public record PartitionDistribution(
        RoutingKey routingKey,
        List<Integer> orderCounts,
        double maxToMeanRatio,
        double clientPairCoLocation,
        double symbolPairCoLocation) {

    public PartitionDistribution {
        orderCounts = List.copyOf(orderCounts);
    }

    public int emptyPartitions() {
        return (int) orderCounts.stream().filter(count -> count == 0).count();
    }
}
