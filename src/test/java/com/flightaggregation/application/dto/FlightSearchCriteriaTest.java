package com.flightaggregation.application.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FlightSearchCriteriaTest {

    @Test
    @DisplayName("Normalizes origin, destination, and carrier filters to uppercase airport and airline codes")
    void normalizesAirportAndCarrierCodes() {
        FlightSearchCriteria criteria = new FlightSearchCriteria(
                " mad ",
                " jfk ",
                LocalDate.of(2026, 10, 1),
                new BigDecimal("250.00"),
                " oa "
        );

        assertEquals("MAD", criteria.origin());
        assertEquals("JFK", criteria.destination());
        assertEquals("OA", criteria.carrier());
    }

    @Test
    @DisplayName("Treats a blank carrier filter as an absent carrier restriction")
    void normalizesBlankCarrierToNoCarrierFilter() {
        FlightSearchCriteria criteria = new FlightSearchCriteria(
                "MAD",
                "JFK",
                LocalDate.of(2026, 10, 1),
                null,
                " "
        );

        assertNull(criteria.carrier());
    }

    @Test
    @DisplayName("Rejects invalid criteria such as short airport codes, identical routes, and negative price filters")
    void rejectsInvalidSearchCriteria() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new FlightSearchCriteria("MA", "JFK", LocalDate.of(2026, 10, 1))
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new FlightSearchCriteria("MAD", "MAD", LocalDate.of(2026, 10, 1))
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new FlightSearchCriteria(
                        "MAD",
                        "JFK",
                        LocalDate.of(2026, 10, 1),
                        new BigDecimal("-0.01"),
                        null
                )
        );
    }
}
