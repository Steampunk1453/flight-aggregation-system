package com.flightaggregation.application.usecase;

import com.flightaggregation.domain.model.FlightItinerary;
import com.flightaggregation.domain.model.Money;

import java.util.Objects;
import java.io.Serializable;

public record SearchFlight(
        FlightItinerary itinerary,
        Money sellingPrice
) implements Serializable {

    public SearchFlight {
        Objects.requireNonNull(itinerary, "Itinerary must not be null");
        Objects.requireNonNull(sellingPrice, "Selling price must not be null");
    }
}
