package com.flightaggregation.application.usecase;

import com.flightaggregation.application.port.in.SearchFlightPage;
import com.flightaggregation.application.port.in.SearchFlights;
import com.flightaggregation.application.port.out.FlightSearchResultStore;

import java.util.Objects;

public final class SearchFlightPageUseCase implements SearchFlightPage {

    private final SearchFlights searchFlights;
    private final FlightSearchResultStore resultStore;

    public SearchFlightPageUseCase(
            SearchFlights searchFlights,
            FlightSearchResultStore resultStore
    ) {
        this.searchFlights = Objects.requireNonNull(searchFlights, "Search flights must not be null");
        this.resultStore = Objects.requireNonNull(resultStore, "Result store must not be null");
    }

    @Override
    public FlightSearchPage search(FlightSearchCriteria criteria, FlightSearchPageRequest pageRequest) {
        Objects.requireNonNull(criteria, "Search criteria must not be null");
        Objects.requireNonNull(pageRequest, "Page request must not be null");
        FlightSearchResult result = searchFlights.search(criteria);
        resultStore.upsertAll(result.itineraries());
        FlightSearchPage page = resultStore.findPage(criteria, pageRequest);
        return new FlightSearchPage(page.flights(), page.nextCursor(), result.failures());
    }
}
