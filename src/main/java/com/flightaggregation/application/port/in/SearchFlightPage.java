package com.flightaggregation.application.port.in;

import com.flightaggregation.application.usecase.FlightSearchCriteria;
import com.flightaggregation.application.usecase.FlightSearchPage;
import com.flightaggregation.application.usecase.FlightSearchPageRequest;

public interface SearchFlightPage {

    FlightSearchPage search(FlightSearchCriteria criteria, FlightSearchPageRequest pageRequest);
}
