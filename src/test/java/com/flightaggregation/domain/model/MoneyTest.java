package com.flightaggregation.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoneyTest {

    @Test
    @DisplayName("When creating an amount, rounds the scale to two decimals and normalizes the currency to uppercase")
    void normalizesScaleAndCurrency() {
        Money money = new Money(new BigDecimal("10"), "eur");

        assertEquals(new BigDecimal("10.00"), money.amount());
        assertEquals("EUR", money.currency());
    }

    @Test
    @DisplayName("Rejects creating an amount when the value is null")
    void rejectsNullAmount() {
        assertThrows(NullPointerException.class, () -> new Money(null, "EUR"));
    }

    @Test
    @DisplayName("Rejects creating an amount when the value is negative")
    void rejectsNegativeAmount() {
        assertThrows(IllegalArgumentException.class, () -> new Money(new BigDecimal("-1"), "EUR"));
    }

    @Test
    @DisplayName("Rejects creating an amount when the currency is blank")
    void rejectsBlankCurrency() {
        assertThrows(IllegalArgumentException.class, () -> new Money(new BigDecimal("10"), " "));
    }

    @Test
    @DisplayName("When multiplying an amount by a factor, scales the result to two decimals while keeping the currency")
    void multiplyScalesTheAmount() {
        Money money = new Money(new BigDecimal("100.00"), "EUR");

        Money result = money.multiply(new BigDecimal("1.10"));

        assertEquals(new BigDecimal("110.00"), result.amount());
        assertEquals("EUR", result.currency());
    }

    @Test
    @DisplayName("Correctly compares whether an amount is less than another when both are in the same currency")
    void isLessThanComparesAmountsInTheSameCurrency() {
        Money cheaper = new Money(new BigDecimal("100.00"), "EUR");
        Money expensive = new Money(new BigDecimal("150.00"), "EUR");

        assertTrue(cheaper.isLessThan(expensive));
        assertFalse(expensive.isLessThan(cheaper));
    }

    @Test
    @DisplayName("Rejects comparing amounts when the currencies are different")
    void isLessThanRejectsDifferentCurrencies() {
        Money euros = new Money(new BigDecimal("100.00"), "EUR");
        Money dollars = new Money(new BigDecimal("100.00"), "USD");

        assertThrows(IllegalArgumentException.class, () -> euros.isLessThan(dollars));
    }
}
