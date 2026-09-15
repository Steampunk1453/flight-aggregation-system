package com.flightaggregation.application.usecase;

import com.flightaggregation.application.dto.FlightSearchCriteria;
import com.flightaggregation.application.dto.FlightSearchPage;
import com.flightaggregation.application.dto.FlightSearchPageRequest;
import com.flightaggregation.application.dto.FlightSearchResult;
import com.flightaggregation.application.port.in.SearchPagedFlights;
import com.flightaggregation.application.port.in.SearchFlights;
import com.flightaggregation.application.port.out.FlightSearchResultStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

public final class SearchPagedFlightsUseCase implements SearchPagedFlights {

    private static final Logger log = LoggerFactory.getLogger(SearchPagedFlightsUseCase.class);

    private final SearchFlights searchFlights;
    private final FlightSearchResultStore resultStore;

    public SearchPagedFlightsUseCase(
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
        log.debug(
                "Materializing {} itineraries for {}->{}",
                result.itineraries().size(), criteria.origin(), criteria.destination()
        );
        resultStore.upsertAll(result.itineraries());
        FlightSearchPage page = resultStore.findPage(criteria, pageRequest);
        return new FlightSearchPage(page.flights(), page.nextCursor(), result.failures());
    }
}
