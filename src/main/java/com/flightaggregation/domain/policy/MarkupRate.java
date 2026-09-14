package com.flightaggregation.domain.policy;

import com.flightaggregation.domain.model.Money;

import java.math.BigDecimal;
import java.util.Objects;

public record MarkupRate(BigDecimal percentage) {

    public MarkupRate {
        Objects.requireNonNull(percentage, "Markup percentage must not be null");
        if (percentage.signum() < 0) {
            throw new IllegalArgumentException("Markup percentage must not be negative");
        }
    }

    public Money applyTo(Money amount) {
        Objects.requireNonNull(amount, "Amount must not be null");
        return amount.multiply(BigDecimal.ONE.add(percentage.movePointLeft(2)));
    }
}
