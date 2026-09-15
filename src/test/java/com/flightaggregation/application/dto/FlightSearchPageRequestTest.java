package com.flightaggregation.application.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class FlightSearchPageRequestTest {

    @Test
    void acceptsTheMinimumAndMaximumPageSize() {
        new FlightSearchPageRequest(1, null);
        new FlightSearchPageRequest(FlightSearchPageRequest.MAX_PAGE_SIZE, null);
    }

    @Test
    void rejectsPageSizesOutsideTheConfiguredBounds() {
        assertThrows(IllegalArgumentException.class, () -> new FlightSearchPageRequest(0, null));
        assertThrows(
                IllegalArgumentException.class,
                () -> new FlightSearchPageRequest(FlightSearchPageRequest.MAX_PAGE_SIZE + 1, null)
        );
    }
}
