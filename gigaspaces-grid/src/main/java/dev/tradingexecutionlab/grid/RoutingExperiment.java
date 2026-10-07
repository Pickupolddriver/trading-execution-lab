package dev.tradingexecutionlab.grid;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Deterministic synthetic experiment for comparing candidate route fields. */
public final class RoutingExperiment {
    private RoutingExperiment() {
    }

    public static List<PartitionDistribution> compare(int orderCount, int clientCount,
                                                        int symbolCount, int partitionCount) {
        if (orderCount < 1 || clientCount < 1 || symbolCount < 1 || partitionCount < 1) {
            throw new IllegalArgumentException("All counts must be positive");
        }

        var orders = new ArrayList<SyntheticOrder>(orderCount);
        var random = new Random(25_031_007L);
        for (int i = 0; i < orderCount; i++) {
            int clientIndex = random.nextInt(clientCount);
            int symbolIndex = random.nextInt(symbolCount);
            orders.add(new SyntheticOrder("order-%06d".formatted(i), clientIndex,
                    "client-%04d".formatted(clientIndex), symbolIndex,
                    "symbol-%03d".formatted(symbolIndex)));
        }

        var results = new ArrayList<PartitionDistribution>();
        for (RoutingKey routingKey : RoutingKey.values()) {
            int[] partitionCounts = new int[partitionCount];
            int[][] clientPartitionCounts = new int[clientCount][partitionCount];
            int[][] symbolPartitionCounts = new int[symbolCount][partitionCount];
            int[] clientOrderCounts = new int[clientCount];
            int[] symbolOrderCounts = new int[symbolCount];

            for (int i = 0; i < orders.size(); i++) {
                SyntheticOrder order = orders.get(i);
                int partition = PartitionRouter.partitionFor(order.routeValue(routingKey), partitionCount);
                partitionCounts[partition]++;
                clientPartitionCounts[order.clientIndex()][partition]++;
                symbolPartitionCounts[order.symbolIndex()][partition]++;
                clientOrderCounts[order.clientIndex()]++;
                symbolOrderCounts[order.symbolIndex()]++;
            }

            results.add(new PartitionDistribution(routingKey, toList(partitionCounts),
                    maxToMean(partitionCounts), pairCoLocation(clientPartitionCounts, clientOrderCounts),
                    pairCoLocation(symbolPartitionCounts, symbolOrderCounts)));
        }
        return List.copyOf(results);
    }

    private static double pairCoLocation(int[][] groupPartitionCounts, int[] groupOrderCounts) {
        long allPairs = 0;
        long localPairs = 0;
        for (int i = 0; i < groupOrderCounts.length; i++) {
            long pairs = (long) groupOrderCounts[i] * (groupOrderCounts[i] - 1) / 2;
            allPairs += pairs;
            for (int count : groupPartitionCounts[i]) {
                localPairs += (long) count * (count - 1) / 2;
            }
        }
        return allPairs == 0 ? 1.0 : (double) localPairs / allPairs;
    }

    private static double maxToMean(int[] counts) {
        int max = java.util.Arrays.stream(counts).max().orElse(0);
        return (double) max / ((double) java.util.Arrays.stream(counts).sum() / counts.length);
    }

    private static List<Integer> toList(int[] values) {
        return java.util.Arrays.stream(values).boxed().toList();
    }

    private record SyntheticOrder(String orderId, int clientIndex, String clientId,
                                  int symbolIndex, String symbol) {
        private String routeValue(RoutingKey key) {
            return switch (key) {
                case ORDER_ID -> orderId;
                case CLIENT_ID -> clientId;
                case SYMBOL -> symbol;
            };
        }
    }
}
