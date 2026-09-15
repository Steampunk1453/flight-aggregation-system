package com.flightaggregation.application.port.in;

import com.flightaggregation.application.dto.FlightSearchCriteria;
import com.flightaggregation.application.dto.FlightSearchPage;
import com.flightaggregation.application.dto.FlightSearchPageRequest;

public interface SearchPagedFlights {

    FlightSearchPage search(FlightSearchCriteria criteria, FlightSearchPageRequest pageRequest);
}
