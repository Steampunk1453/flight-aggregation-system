package com.flightaggregation.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FlightSegmentTest {

    private static final Carrier CARRIER = new Carrier("AB", "Mock Air");

    @Test
    @DisplayName("When creating a segment, normalizes the flight number and airport codes to uppercase without spaces")
    void normalizesCodesAndFlightNumber() {
        FlightSegment segment = new FlightSegment(
                CARRIER,
                " ab123 ",
                " mad ",
                " lhr ",
                OffsetDateTime.parse("2026-10-01T10:00:00+02:00"),
                OffsetDateTime.parse("2026-10-01T11:30:00+01:00")
        );

        assertEquals("AB123", segment.flightNumber());
        assertEquals("MAD", segment.origin());
        assertEquals("LHR", segment.destination());
    }

    @Test
    @DisplayName("Rejects creating a segment when the carrier is null")
    void rejectsNullCarrier() {
        assertThrows(
                NullPointerException.class,
                () -> new FlightSegment(
                        null,
                        "AB123",
                        "MAD",
                        "LHR",
                        OffsetDateTime.parse("2026-10-01T10:00:00+02:00"),
                        OffsetDateTime.parse("2026-10-01T11:30:00+01:00")
                )
        );
    }

    @Test
    @DisplayName("Rejects creating a segment when the flight number is blank")
    void rejectsBlankFlightNumber() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new FlightSegment(
                        CARRIER,
                        " ",
                        "MAD",
                        "LHR",
                        OffsetDateTime.parse("2026-10-01T10:00:00+02:00"),
                        OffsetDateTime.parse("2026-10-01T11:30:00+01:00")
                )
        );
    }

    @Test
    @DisplayName("Rejects creating a segment when the airport code does not have a valid length")
    void rejectsAirportCodesWithInvalidLength() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new FlightSegment(
                        CARRIER,
                        "AB123",
                        "MADX",
                        "LHR",
                        OffsetDateTime.parse("2026-10-01T10:00:00+02:00"),
                        OffsetDateTime.parse("2026-10-01T11:30:00+01:00")
                )
        );
    }

    @Test
    @DisplayName("Rejects creating a segment when origin and destination are the same")
    void rejectsSameOriginAndDestination() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new FlightSegment(
                        CARRIER,
                        "AB123",
                        "MAD",
                        "mad",
                        OffsetDateTime.parse("2026-10-01T10:00:00+02:00"),
                        OffsetDateTime.parse("2026-10-01T11:30:00+01:00")
                )
        );
    }

    @Test
    @DisplayName("Rejects creating a segment when arrival is not after departure")
    void rejectsArrivalNotAfterDeparture() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new FlightSegment(
                        CARRIER,
                        "AB123",
                        "MAD",
                        "LHR",
                        OffsetDateTime.parse("2026-10-01T11:30:00+01:00"),
                        OffsetDateTime.parse("2026-10-01T10:00:00+02:00")
                )
        );
    }
}
