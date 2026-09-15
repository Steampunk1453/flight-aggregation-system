package com.flightaggregation.domain.policy;

import com.flightaggregation.domain.model.Carrier;
import com.flightaggregation.domain.model.FlightItinerary;
import com.flightaggregation.domain.model.FlightSegment;
import com.flightaggregation.domain.model.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FixedMarkupPolicyTest {

    private static final Carrier CARRIER = new Carrier("AB", "Mock Air");

    @Test
    @DisplayName("Computes the selling price by applying the configured fixed markup rate to the supplier price")
    void computesSellingPriceUsingTheConfiguredRate() {
        FixedMarkupPolicy policy = new FixedMarkupPolicy(new MarkupRate(new BigDecimal("10")));
        FlightItinerary itinerary = itinerary(new Money(new BigDecimal("100.00"), "EUR"));

        Money sellingPrice = policy.sellingPriceFor(itinerary);

        assertEquals(new BigDecimal("110.00"), sellingPrice.amount());
        assertEquals("EUR", sellingPrice.currency());
    }

    @Test
    @DisplayName("Rejects creating the policy when the markup rate is null")
    void rejectsNullMarkupRate() {
        assertThrows(NullPointerException.class, () -> new FixedMarkupPolicy(null));
    }

    @Test
    @DisplayName("Rejects computing the selling price when the itinerary is null")
    void rejectsNullItinerary() {
        FixedMarkupPolicy policy = new FixedMarkupPolicy(new MarkupRate(new BigDecimal("10")));

        assertThrows(NullPointerException.class, () -> policy.sellingPriceFor(null));
    }

    private static FlightItinerary itinerary(Money supplierPrice) {
        return new FlightItinerary(
                List.of(new FlightSegment(
                        CARRIER,
                        "AB123",
                        "MAD",
                        "LHR",
                        OffsetDateTime.parse("2026-10-01T10:00:00+02:00"),
                        OffsetDateTime.parse("2026-10-01T11:30:00+01:00")
                )),
                supplierPrice,
                "ALPHA"
        );
    }
}
