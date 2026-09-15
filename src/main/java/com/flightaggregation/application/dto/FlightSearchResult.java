package com.flightaggregation.application.dto;

import java.util.List;
import java.io.Serializable;
import java.util.Objects;

public record FlightSearchResult(
        List<FlightOffer> itineraries,
        List<ProviderSearchFailure> failures
) implements Serializable {

    public FlightSearchResult {
        Objects.requireNonNull(itineraries, "Itineraries must not be null");
        Objects.requireNonNull(failures, "Failures must not be null");
        itineraries = List.copyOf(itineraries);
        failures = List.copyOf(failures);
    }
}
