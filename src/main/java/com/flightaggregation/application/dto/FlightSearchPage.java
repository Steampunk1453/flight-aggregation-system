package com.flightaggregation.application.dto;

import java.util.List;
import java.util.Objects;

public record FlightSearchPage(
        List<FlightOffer> flights,
        FlightSearchPageRequest.FlightSearchCursor nextCursor,
        List<ProviderSearchFailure> providerFailures
) {

    public FlightSearchPage {
        Objects.requireNonNull(flights, "Flights must not be null");
        Objects.requireNonNull(providerFailures, "Provider failures must not be null");
        flights = List.copyOf(flights);
        providerFailures = List.copyOf(providerFailures);
    }
}
