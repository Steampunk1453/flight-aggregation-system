package com.flightaggregation.domain.model;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public record FlightItinerary(
        List<FlightSegment> segments,
        Money supplierPrice,
        String provider
) implements Serializable {

    public FlightItinerary {
        Objects.requireNonNull(segments, "Segments must not be null");
        Objects.requireNonNull(supplierPrice, "Supplier price must not be null");
        if (segments.isEmpty()) {
            throw new IllegalArgumentException("An itinerary must contain at least one segment");
        }
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("Provider must not be blank");
        }
        segments = List.copyOf(segments);
        provider = provider.trim().toUpperCase();
        validateConnections(segments);
    }

    public String origin() {
        return segments.getFirst().origin();
    }

    public String destination() {
        return segments.getLast().destination();
    }

    public OffsetDateTime departureAt() {
        return segments.getFirst().departureAt();
    }

    public String deduplicationKey() {
        return segments.stream()
                .map(segment -> String.join("|",
                        segment.carrier().code(),
                        segment.flightNumber(),
                        segment.origin(),
                        segment.destination(),
                        segment.departureAt().toInstant().toString()))
                .collect(Collectors.joining(">>"));
    }

    public Duration totalTravelTime() {
        return Duration.between(departureAt(), segments.getLast().arrivalAt());
    }

    private static void validateConnections(List<FlightSegment> segments) {
        for (int index = 1; index < segments.size(); index++) {
            FlightSegment previous = segments.get(index - 1);
            FlightSegment current = segments.get(index);
            if (!previous.destination().equals(current.origin())) {
                throw new IllegalArgumentException("Segments contain an invalid connection");
            }
            if (!current.departureAt().isAfter(previous.arrivalAt())) {
                throw new IllegalArgumentException("Segments contain an invalid connection time");
            }
        }
    }
}
