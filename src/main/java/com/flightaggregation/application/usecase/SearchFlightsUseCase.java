package com.flightaggregation.application.usecase;

import com.flightaggregation.domain.model.FlightItinerary;
import com.flightaggregation.domain.model.FlightSearchQuery;
import com.flightaggregation.domain.repository.FlightProviderRepository;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

public final class SearchFlightsUseCase {

    private final List<FlightProviderRepository> providers;

    public SearchFlightsUseCase(Collection<FlightProviderRepository> providers) {
        Objects.requireNonNull(providers, "Providers must not be null");
        if (providers.isEmpty()) {
            throw new IllegalArgumentException("At least one flight provider is required");
        }
        this.providers = List.copyOf(providers);
    }

    public FlightSearchResult search(FlightSearchQuery query) {
        Objects.requireNonNull(query, "Search query must not be null");
        return new FlightSearchResult(
                providers.stream()
                        .map(provider -> provider.search(query))
                        .flatMap(result -> result.itineraries().stream())
                        .toList()
        );
    }
}
