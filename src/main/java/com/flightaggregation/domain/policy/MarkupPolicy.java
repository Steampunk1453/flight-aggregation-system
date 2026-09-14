package com.flightaggregation.domain.policy;

import com.flightaggregation.domain.model.FlightItinerary;
import com.flightaggregation.domain.model.Money;

public interface MarkupPolicy {

    Money sellingPriceFor(FlightItinerary itinerary);
}
