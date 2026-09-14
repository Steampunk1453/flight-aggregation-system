package com.flightaggregation.domain.model;

import com.flightaggregation.domain.policy.FixedMarkupPolicy;
import com.flightaggregation.domain.policy.MarkupRate;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FlightItineraryTest {

    private static final Carrier CARRIER = new Carrier("ab", "Mock Air");

    @Test
    void appliesTheConfiguredMarkupPolicy() {
        FlightItinerary itinerary = new FlightItinerary(
                List.of(segment("MAD", "LHR", "2026-10-01T10:00:00+02:00", "2026-10-01T11:30:00+01:00")),
                new Money(new BigDecimal("100.00"), "eur"),
                "ALPHA"
        );
        var markupPolicy = new FixedMarkupPolicy(new MarkupRate(new BigDecimal("7.50")));

        assertEquals(new BigDecimal("107.50"), markupPolicy.sellingPriceFor(itinerary).amount());
        assertEquals("EUR", markupPolicy.sellingPriceFor(itinerary).currency());
    }

    @Test
    void createsStableDeduplicationKeyFromItineraryIdentity() {
        FlightItinerary itinerary = new FlightItinerary(
                List.of(segment("MAD", "LHR", "2026-10-01T10:00:00+02:00", "2026-10-01T11:30:00+01:00")),
                new Money(new BigDecimal("100.00"), "EUR"),
                "mock-alpha"
        );

        assertEquals(
                "AB|AB123|MAD|LHR|2026-10-01T08:00:00Z",
                itinerary.deduplicationKey()
        );
    }

    @Test
    void rejectsInvalidConnections() {
        FlightSegment first = segment("MAD", "LHR", "2026-10-01T10:00:00+02:00", "2026-10-01T11:30:00+01:00");
        FlightSegment second = segment("CDG", "JFK", "2026-10-01T13:00:00+02:00", "2026-10-01T16:00:00-04:00");

        assertThrows(
                IllegalArgumentException.class,
                () -> new FlightItinerary(
                        List.of(first, second),
                        new Money(new BigDecimal("100.00"), "EUR"),
                        "mock-alpha"
                )
        );
    }

    private static FlightSegment segment(
            String origin,
            String destination,
            String departure,
            String arrival
    ) {
        return new FlightSegment(
                CARRIER,
                "AB123",
                origin,
                destination,
                OffsetDateTime.parse(departure),
                OffsetDateTime.parse(arrival)
        );
    }
}
