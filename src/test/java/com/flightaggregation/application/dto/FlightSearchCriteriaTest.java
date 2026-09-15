package com.flightaggregation.application.dto;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FlightSearchCriteriaTest {

    @Test
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
