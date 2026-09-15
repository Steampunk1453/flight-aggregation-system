package com.flightaggregation.application.usecase;

import com.flightaggregation.application.dto.FlightOffer;
import com.flightaggregation.application.dto.FlightSearchCriteria;
import com.flightaggregation.application.dto.FlightSearchPage;
import com.flightaggregation.application.dto.FlightSearchPageRequest;
import com.flightaggregation.application.dto.FlightSearchResult;
import com.flightaggregation.application.dto.ProviderSearchFailure;
import com.flightaggregation.application.port.in.SearchFlights;
import com.flightaggregation.application.port.out.FlightSearchResultStore;
import com.flightaggregation.domain.model.Carrier;
import com.flightaggregation.domain.model.FlightItinerary;
import com.flightaggregation.domain.model.FlightSegment;
import com.flightaggregation.domain.model.Money;
import com.flightaggregation.domain.model.Provider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SearchPagedFlightsUseCaseTest {

    private static final FlightSearchCriteria CRITERIA =
            new FlightSearchCriteria("MAD", "JFK", LocalDate.of(2026, 10, 1));
    private static final FlightSearchPageRequest PAGE_REQUEST =
            new FlightSearchPageRequest(1, null);

    @Test
    @DisplayName("Materializes search results and returns the requested page along with provider failures")
    void materializesSearchResultsAndReturnsTheRequestedPageWithProviderFailures() {
        SearchFlights searchFlights = mock(SearchFlights.class);
        FlightSearchResultStore resultStore = mock(FlightSearchResultStore.class);
        FlightOffer offer = flightOffer();
        ProviderSearchFailure failure = new ProviderSearchFailure(Provider.GAMMA, "Timed out");
        FlightSearchResult searchResult = new FlightSearchResult(List.of(offer), List.of(failure));
        FlightSearchPageRequest.FlightSearchCursor nextCursor =
                new FlightSearchPageRequest.FlightSearchCursor(
                        OffsetDateTime.parse("2026-10-01T10:00:00Z"),
                        new BigDecimal("120.00"),
                        "offer-1"
                );
        FlightSearchPage storedPage = new FlightSearchPage(List.of(offer), nextCursor, List.of());
        when(searchFlights.search(CRITERIA)).thenReturn(searchResult);
        when(resultStore.findPage(CRITERIA, PAGE_REQUEST)).thenReturn(storedPage);

        FlightSearchPage result = new SearchPagedFlightsUseCase(searchFlights, resultStore)
                .search(CRITERIA, PAGE_REQUEST);

        assertEquals(storedPage.flights(), result.flights());
        assertEquals(nextCursor, result.nextCursor());
        assertEquals(List.of(failure), result.providerFailures());
        InOrder calls = inOrder(searchFlights, resultStore);
        calls.verify(searchFlights).search(CRITERIA);
        calls.verify(resultStore).upsertAll(List.of(offer));
        calls.verify(resultStore).findPage(CRITERIA, PAGE_REQUEST);
    }

    private static FlightOffer flightOffer() {
        FlightSegment segment = new FlightSegment(
                new Carrier("OA", "Omega Air"),
                "OA101",
                "MAD",
                "JFK",
                OffsetDateTime.parse("2026-10-01T10:00:00Z"),
                OffsetDateTime.parse("2026-10-01T12:00:00Z")
        );
        Money price = new Money(new BigDecimal("120.00"), "EUR");
        return new FlightOffer(new FlightItinerary(List.of(segment), price, "BETA"), price);
    }
}
