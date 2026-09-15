package com.flightaggregation.infrastructure.web;

import com.flightaggregation.application.dto.FlightSearchPageRequest;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;

final class FlightSearchCursorCodec {

    private FlightSearchCursorCodec() {
    }

    static String encode(FlightSearchPageRequest.FlightSearchCursor cursor) {
        if (cursor == null) {
            return null;
        }
        String value = String.join(
                "|",
                cursor.departureAt().toString(),
                cursor.sellingPrice().toPlainString(),
                cursor.id()
        );
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    static FlightSearchPageRequest.FlightSearchCursor decode(String encodedCursor) {
        if (encodedCursor == null || encodedCursor.isBlank()) {
            return null;
        }
        try {
            String value = new String(
                    Base64.getUrlDecoder().decode(encodedCursor),
                    StandardCharsets.UTF_8
            );
            String[] parts = value.split("\\|", -1);
            if (parts.length != 3) {
                throw new IllegalArgumentException("Invalid pagination cursor");
            }
            return new FlightSearchPageRequest.FlightSearchCursor(
                    OffsetDateTime.parse(parts[0]),
                    new BigDecimal(parts[1]),
                    parts[2]
            );
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid pagination cursor");
        }
    }
}
