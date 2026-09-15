package com.flightaggregation.application.dto;

import java.util.Objects;

public record FlightSearchPageRequest(int pageSize, FlightSearchCursor cursor) {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    public FlightSearchPageRequest {
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("Page size must be between 1 and " + MAX_PAGE_SIZE);
        }
    }

    public record FlightSearchCursor(
            java.time.OffsetDateTime departureAt,
            java.math.BigDecimal sellingPrice,
            String id
    ) {

        public FlightSearchCursor {
            Objects.requireNonNull(departureAt, "Cursor departure time must not be null");
            Objects.requireNonNull(sellingPrice, "Cursor selling price must not be null");
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("Cursor ID must not be blank");
            }
        }
    }
}
