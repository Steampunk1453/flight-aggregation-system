package com.flightaggregation.domain.policy;

import com.flightaggregation.domain.model.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MarkupRateTest {

    @Test
    @DisplayName("Applies the markup percentage to the amount and returns the increased value")
    void appliesPercentageToAmount() {
        MarkupRate markupRate = new MarkupRate(new BigDecimal("10"));

        Money result = markupRate.applyTo(new Money(new BigDecimal("100.00"), "EUR"));

        assertEquals(new BigDecimal("110.00"), result.amount());
        assertEquals("EUR", result.currency());
    }

    @Test
    @DisplayName("With a zero percent markup rate, the amount remains unchanged")
    void zeroPercentageLeavesAmountUnchanged() {
        MarkupRate markupRate = new MarkupRate(BigDecimal.ZERO);

        Money result = markupRate.applyTo(new Money(new BigDecimal("100.00"), "EUR"));

        assertEquals(new BigDecimal("100.00"), result.amount());
    }

    @Test
    @DisplayName("Rejects creating the markup rate when the percentage is null")
    void rejectsNullPercentage() {
        assertThrows(NullPointerException.class, () -> new MarkupRate(null));
    }

    @Test
    @DisplayName("Rejects creating the markup rate when the percentage is negative")
    void rejectsNegativePercentage() {
        assertThrows(IllegalArgumentException.class, () -> new MarkupRate(new BigDecimal("-1")));
    }

    @Test
    @DisplayName("Rejects applying the markup when the amount is null")
    void rejectsNullAmount() {
        MarkupRate markupRate = new MarkupRate(new BigDecimal("10"));

        assertThrows(NullPointerException.class, () -> markupRate.applyTo(null));
    }
}
