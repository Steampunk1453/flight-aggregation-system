package com.flightaggregation.domain.model;

import java.time.OffsetDateTime;
import java.io.Serializable;
import java.util.Objects;

public record FlightSegment(
        Carrier carrier,
        String flightNumber,
        String origin,
        String destination,
        OffsetDateTime departureAt,
        OffsetDateTime arrivalAt
) implements Serializable {

    public FlightSegment {
        Objects.requireNonNull(carrier, "Carrier must not be null");
        requireCode(origin, "Origin");
        requireCode(destination, "Destination");
        if (flightNumber == null || flightNumber.isBlank()) {
            throw new IllegalArgumentException("Flight number must not be blank");
        }
        Objects.requireNonNull(departureAt, "Departure time must not be null");
        Objects.requireNonNull(arrivalAt, "Arrival time must not be null");
        if (!arrivalAt.isAfter(departureAt)) {
            throw new IllegalArgumentException("Arrival must be after departure");
        }
        if (origin.equalsIgnoreCase(destination)) {
            throw new IllegalArgumentException("Origin and destination must differ");
        }
        origin = origin.trim().toUpperCase();
        destination = destination.trim().toUpperCase();
        flightNumber = flightNumber.trim().toUpperCase();
    }

    private static void requireCode(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        if (value.trim().length() != 3) {
            throw new IllegalArgumentException(field + " must be a three-letter airport code");
        }
    }
}
