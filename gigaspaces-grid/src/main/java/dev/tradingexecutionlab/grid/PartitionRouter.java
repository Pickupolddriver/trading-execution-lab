package dev.tradingexecutionlab.grid;

import java.util.Objects;

/** Mirrors GigaSpaces' documented hash-based routing calculation for a routing value. */
public final class PartitionRouter {
    private PartitionRouter() {
    }

    public static int partitionFor(String routingValue, int partitionCount) {
        Objects.requireNonNull(routingValue, "routingValue");
        if (partitionCount < 1) {
            throw new IllegalArgumentException("partitionCount must be positive");
        }

        int hash = routingValue.hashCode();
        int safeAbsoluteHash = hash == Integer.MIN_VALUE ? Integer.MAX_VALUE : Math.abs(hash);
        return safeAbsoluteHash % partitionCount;
    }
}
