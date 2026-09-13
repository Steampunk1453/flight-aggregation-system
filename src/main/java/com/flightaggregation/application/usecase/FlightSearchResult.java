package com.flightaggregation.application.usecase;

import com.flightaggregation.domain.model.FlightItinerary;

import java.util.List;
import java.util.Objects;

public record FlightSearchResult(List<FlightItinerary> itineraries) {

    public FlightSearchResult {
        Objects.requireNonNull(itineraries, "Itineraries must not be null");
        itineraries = List.copyOf(itineraries);
    }
}
