package com.flightaggregation.infrastructure.web;

import com.flightaggregation.application.dto.FlightSearchPageRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FlightSearchCursorCodecTest {

    @Test
    void roundTripsTheCompleteKeysetBoundary() {
        FlightSearchPageRequest.FlightSearchCursor cursor =
                new FlightSearchPageRequest.FlightSearchCursor(
                        OffsetDateTime.parse("2026-10-01T10:00:00Z"),
                        new BigDecimal("120.75"),
                        "offer-1"
                );

        assertEquals(cursor, FlightSearchCursorCodec.decode(FlightSearchCursorCodec.encode(cursor)));
    }

    @Test
    void treatsAnAbsentCursorAsTheFirstPage() {
        assertNull(FlightSearchCursorCodec.decode(null));
        assertNull(FlightSearchCursorCodec.decode(" "));
    }

    @Test
    void rejectsMalformedCursors() {
        assertThrows(IllegalArgumentException.class, () -> FlightSearchCursorCodec.decode("not-a-cursor"));
    }
}
