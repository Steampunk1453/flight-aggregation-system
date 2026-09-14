package com.flightaggregation.application.port.in;

import com.flightaggregation.application.usecase.FlightSearchCriteria;
import com.flightaggregation.application.usecase.FlightSearchResult;

public interface SearchFlights {

    FlightSearchResult search(FlightSearchCriteria criteria);
}
