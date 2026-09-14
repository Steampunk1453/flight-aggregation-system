package com.flightaggregation.application.port.out;

import com.flightaggregation.application.usecase.FlightSearchCriteria;
import com.flightaggregation.application.usecase.FlightSearchPage;
import com.flightaggregation.application.usecase.FlightSearchPageRequest;
import com.flightaggregation.application.usecase.SearchFlight;

import java.util.List;

public interface FlightSearchResultStore {

    void upsertAll(List<SearchFlight> flights);

    FlightSearchPage findPage(FlightSearchCriteria criteria, FlightSearchPageRequest pageRequest);
}
