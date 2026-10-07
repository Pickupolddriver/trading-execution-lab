package dev.tradingexecutionlab.grid;

/** Candidate business keys for placing related trading data in one partition. */
public enum RoutingKey {
    ORDER_ID,
    CLIENT_ID,
    SYMBOL
}
