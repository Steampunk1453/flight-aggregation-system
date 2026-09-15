package com.flightaggregation.application.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class FlightSearchPageRequestTest {

    @Test
    @DisplayName("Accepts page sizes at both the minimum and configured maximum boundaries")
    void acceptsTheMinimumAndMaximumPageSize() {
        new FlightSearchPageRequest(1, null);
        new FlightSearchPageRequest(FlightSearchPageRequest.MAX_PAGE_SIZE, null);
    }

    @Test
    @DisplayName("Rejects page sizes outside the supported pagination bounds")
    void rejectsPageSizesOutsideTheConfiguredBounds() {
        assertThrows(IllegalArgumentException.class, () -> new FlightSearchPageRequest(0, null));
        assertThrows(
                IllegalArgumentException.class,
                () -> new FlightSearchPageRequest(FlightSearchPageRequest.MAX_PAGE_SIZE + 1, null)
        );
    }
}
