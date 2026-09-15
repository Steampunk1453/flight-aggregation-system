package com.flightaggregation.application.port.out;

import com.flightaggregation.application.dto.FlightSearchCriteria;
import com.flightaggregation.domain.model.FlightItinerary;
import com.flightaggregation.domain.model.Provider;

import java.util.List;

public interface FlightSearchProvider {

    Provider provider();

    List<FlightItinerary> search(FlightSearchCriteria criteria);
}
