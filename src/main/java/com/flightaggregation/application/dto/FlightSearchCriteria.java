package com.flightaggregation.application.dto;

import java.time.LocalDate;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;

public record FlightSearchCriteria(
        String origin,
        String destination,
        LocalDate departureDate,
        BigDecimal maxPrice,
        String carrier
) implements Serializable {

    public FlightSearchCriteria(String origin, String destination, LocalDate departureDate) {
        this(origin, destination, departureDate, null, null);
    }

    public FlightSearchCriteria {
        origin = normalizeAirportCode(origin, "Origin");
        destination = normalizeAirportCode(destination, "Destination");
        Objects.requireNonNull(departureDate, "Departure date must not be null");
        if (maxPrice != null && maxPrice.signum() < 0) {
            throw new IllegalArgumentException("Maximum price must not be negative");
        }
        carrier = normalizeCarrier(carrier);
        if (origin.equals(destination)) {
            throw new IllegalArgumentException("Origin and destination must differ");
        }
    }

    private static String normalizeAirportCode(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        String normalized = value.trim().toUpperCase();
        if (normalized.length() != 3) {
            throw new IllegalArgumentException(field + " must be a three-letter airport code");
        }
        return normalized;
    }

    private static String normalizeCarrier(String carrier) {
        if (carrier == null || carrier.isBlank()) {
            return null;
        }
        String normalized = carrier.trim().toUpperCase();
        if (normalized.length() < 2 || normalized.length() > 3) {
            throw new IllegalArgumentException("Carrier must be a two- or three-letter code");
        }
        return normalized;
    }
}
