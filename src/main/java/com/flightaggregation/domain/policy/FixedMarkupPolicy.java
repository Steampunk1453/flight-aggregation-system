package com.flightaggregation.domain.policy;

import com.flightaggregation.domain.model.FlightItinerary;
import com.flightaggregation.domain.model.Money;

import java.util.Objects;

public final class FixedMarkupPolicy implements MarkupPolicy {

    private final MarkupRate markupRate;

    public FixedMarkupPolicy(MarkupRate markupRate) {
        this.markupRate = Objects.requireNonNull(markupRate, "Markup rate must not be null");
    }

    @Override
    public Money sellingPriceFor(FlightItinerary itinerary) {
        Objects.requireNonNull(itinerary, "Itinerary must not be null");
        return markupRate.applyTo(itinerary.supplierPrice());
    }
}
