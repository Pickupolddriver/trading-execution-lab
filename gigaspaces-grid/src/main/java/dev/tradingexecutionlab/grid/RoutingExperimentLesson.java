package dev.tradingexecutionlab.grid;

/** Runs a repeatable routing-distribution experiment without starting a cluster. */
public class RoutingExperimentLesson {
    public static void main(String[] args) {
        System.out.println("Synthetic GigaSpaces-style routing experiment");
        System.out.println("10,000 orders | 500 clients | 50 symbols | 16 partitions");
        RoutingExperiment.compare(10_000, 500, 50, 16).forEach(result ->
                System.out.printf("%-8s counts=%s max/mean=%.2f clientPairsLocal=%.1f%% symbolPairsLocal=%.1f%%%n",
                        result.routingKey(), result.orderCounts(), result.maxToMeanRatio(),
                        result.clientPairCoLocation() * 100, result.symbolPairCoLocation() * 100));
        System.out.println("These are hash-placement results, not latency or cluster benchmarks.");
    }
}
