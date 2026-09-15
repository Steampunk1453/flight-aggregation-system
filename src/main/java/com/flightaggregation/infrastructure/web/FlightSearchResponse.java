package com.flightaggregation.infrastructure.web;

import com.flightaggregation.application.dto.FlightSearchPage;
import com.flightaggregation.domain.model.FlightSegment;
import com.flightaggregation.domain.model.Money;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record FlightSearchResponse(
        List<FlightSearchResultDto> flights,
        List<ProviderFailureDto> providerFailures,
        String nextCursor
) {

    public FlightSearchResponse {
        flights = List.copyOf(flights);
        providerFailures = List.copyOf(providerFailures);
    }

    static FlightSearchResponse from(FlightSearchPage page) {
        return new FlightSearchResponse(
                page.flights().stream()
                        .map(flight -> new FlightSearchResultDto(
                                flight.itinerary().provider(),
                                flight.itinerary().segments().stream()
                                        .map(FlightSegmentDto::from)
                                        .toList(),
                                MoneyDto.from(flight.itinerary().supplierPrice()),
                                MoneyDto.from(flight.sellingPrice())
                        ))
                        .toList(),
                page.providerFailures().stream()
                        .map(failure -> new ProviderFailureDto(
                                failure.provider().name(),
                                failure.reason()
                        ))
                        .toList(),
                FlightSearchCursorCodec.encode(page.nextCursor())
        );
    }

    public record FlightSearchResultDto(
            String provider,
            List<FlightSegmentDto> segments,
            MoneyDto supplierPrice,
            MoneyDto sellingPrice
    ) {

        public FlightSearchResultDto {
            segments = List.copyOf(segments);
        }
    }

    public record FlightSegmentDto(
            String carrierCode,
            String carrierName,
            String flightNumber,
            String origin,
            String destination,
            OffsetDateTime departureAt,
            OffsetDateTime arrivalAt
    ) {

        private static FlightSegmentDto from(FlightSegment segment) {
            return new FlightSegmentDto(
                    segment.carrier().code(),
                    segment.carrier().name(),
                    segment.flightNumber(),
                    segment.origin(),
                    segment.destination(),
                    segment.departureAt(),
                    segment.arrivalAt()
            );
        }
    }

    public record MoneyDto(BigDecimal amount, String currency) {

        private static MoneyDto from(Money money) {
            return new MoneyDto(money.amount(), money.currency());
        }
    }

    public record ProviderFailureDto(String provider, String reason) {
    }
}
