package com.flightaggregation.application.usecase;

import java.util.List;
import java.io.Serializable;
import java.util.Objects;

public record FlightSearchResult(
        List<SearchFlight> itineraries,
        List<ProviderSearchFailure> failures
) implements Serializable {

    public FlightSearchResult {
        Objects.requireNonNull(itineraries, "Itineraries must not be null");
        Objects.requireNonNull(failures, "Failures must not be null");
        itineraries = List.copyOf(itineraries);
        failures = List.copyOf(failures);
    }
}
