package dev.tradingexecutionlab.domain;

/** Result of an optimistic, idempotent execution update. */
public enum ExecutionApplyResult {
    APPLIED,
    DUPLICATE,
    VERSION_CONFLICT
}
