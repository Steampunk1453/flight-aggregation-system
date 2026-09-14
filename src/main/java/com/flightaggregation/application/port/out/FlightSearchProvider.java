package com.flightaggregation.application.port.out;

import com.flightaggregation.application.usecase.FlightSearchCriteria;
import com.flightaggregation.domain.model.FlightItinerary;
import com.flightaggregation.domain.model.ProviderId;

import java.util.List;

public interface FlightSearchProvider {

    ProviderId provider();

    List<FlightItinerary> search(FlightSearchCriteria criteria);
}
