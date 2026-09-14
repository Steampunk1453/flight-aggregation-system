package com.flightaggregation.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.io.Serializable;
import java.util.Objects;

public record Money(BigDecimal amount, String currency) implements Serializable {

    public Money {
        Objects.requireNonNull(amount, "Amount must not be null");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("Amount must not be negative");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("Currency must not be blank");
        }
        amount = amount.setScale(2, RoundingMode.HALF_UP);
        currency = currency.trim().toUpperCase();
    }

    public Money multiply(BigDecimal multiplier) {
        Objects.requireNonNull(multiplier, "Multiplier must not be null");
        return new Money(amount.multiply(multiplier), currency);
    }

    public boolean isLessThan(Money other) {
        Objects.requireNonNull(other, "Other money value must not be null");
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException("Money values must use the same currency");
        }
        return amount.compareTo(other.amount) < 0;
    }
}
