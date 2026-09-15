package com.flightaggregation.application.port.in;

import com.flightaggregation.application.dto.FlightSearchCriteria;
import com.flightaggregation.application.dto.FlightSearchResult;

public interface SearchFlights {

    FlightSearchResult search(FlightSearchCriteria criteria);
}
