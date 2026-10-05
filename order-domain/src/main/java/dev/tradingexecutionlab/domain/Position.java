package dev.tradingexecutionlab.domain;

import lombok.Getter;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** Net position for one symbol, using average cost to calculate realized PnL. */
@Getter
public class Position {
    private final String symbol;
    /** Positive is long; negative is short. */
    private BigDecimal quantity = BigDecimal.ZERO;
    private BigDecimal averagePrice = BigDecimal.ZERO;
    private BigDecimal realizedPnl = BigDecimal.ZERO;

    @Getter(lombok.AccessLevel.NONE)
    private final Set<String> appliedExecutionIds = new HashSet<>();

    public Position(String symbol) {
        Objects.requireNonNull(symbol, "symbol");
        if (symbol.isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank");
        }
        this.symbol = symbol;
    }

    public void applyExecution(Side side, Execution execution) {
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(execution, "execution");
        if (appliedExecutionIds.contains(execution.executionId())) {
            return;
        }

        BigDecimal signedFill = side == Side.BUY
                ? execution.quantity()
                : execution.quantity().negate();
        BigDecimal newQuantity = quantity.add(signedFill);

        if (quantity.signum() == 0 || quantity.signum() == signedFill.signum()) {
            BigDecimal currentValue = averagePrice.multiply(quantity.abs());
            BigDecimal fillValue = execution.price().multiply(execution.quantity());
            averagePrice = currentValue.add(fillValue)
                    .divide(quantity.abs().add(execution.quantity()), MathContext.DECIMAL128);
        } else {
            BigDecimal closedQuantity = quantity.abs().min(execution.quantity());
            BigDecimal direction = BigDecimal.valueOf(quantity.signum());
            BigDecimal pnlPerUnit = execution.price().subtract(averagePrice).multiply(direction);
            realizedPnl = realizedPnl.add(pnlPerUnit.multiply(closedQuantity));

            if (newQuantity.signum() == 0) {
                averagePrice = BigDecimal.ZERO;
            } else if (newQuantity.signum() != quantity.signum()) {
                averagePrice = execution.price();
            }
        }

        quantity = newQuantity;
        appliedExecutionIds.add(execution.executionId());
    }

    public boolean isFlat() {
        return quantity.signum() == 0;
    }
}
