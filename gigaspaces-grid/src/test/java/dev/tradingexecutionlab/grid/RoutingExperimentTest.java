package dev.tradingexecutionlab.grid;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RoutingExperimentTest {
    @Test
    void sameRoutingValueAlwaysMapsToTheSamePartition() {
        assertEquals(PartitionRouter.partitionFor("client-1", 16),
                PartitionRouter.partitionFor("client-1", 16));
    }

    @Test
    void routeComparisonShowsAffinityAndDistributionTradeOff() {
        var results = RoutingExperiment.compare(10_000, 500, 50, 16);
        var byRoute = results.stream().collect(java.util.stream.Collectors.toMap(
                PartitionDistribution::routingKey, result -> result));

        assertAll(
                () -> assertEquals(3, results.size()),
                () -> assertEquals(1.0, byRoute.get(RoutingKey.CLIENT_ID).clientPairCoLocation()),
                () -> assertEquals(1.0, byRoute.get(RoutingKey.SYMBOL).symbolPairCoLocation()),
                () -> assertTrue(byRoute.get(RoutingKey.SYMBOL).clientPairCoLocation() < 0.2),
                () -> assertTrue(byRoute.get(RoutingKey.CLIENT_ID).symbolPairCoLocation() < 0.2),
                () -> assertTrue(byRoute.get(RoutingKey.ORDER_ID).maxToMeanRatio() < 1.2),
                () -> assertEquals(16, byRoute.get(RoutingKey.ORDER_ID).orderCounts().size()));
    }

    @Test
    void validatesPartitionCountAndUsesAllPartitionsForTypicalKeys() {
        assertThrows(IllegalArgumentException.class, () -> PartitionRouter.partitionFor("key", 0));
        assertEquals(0, RoutingExperiment.compare(100, 10, 5, 1)
                .getFirst().emptyPartitions());
    }
}
