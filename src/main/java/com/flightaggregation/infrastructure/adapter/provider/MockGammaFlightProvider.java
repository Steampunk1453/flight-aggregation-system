package com.flightaggregation.infrastructure.adapter.provider;

import com.flightaggregation.application.dto.FlightSearchCriteria;
import com.flightaggregation.application.port.out.FlightSearchProvider;
import com.flightaggregation.domain.model.Carrier;
import com.flightaggregation.domain.model.FlightItinerary;
import com.flightaggregation.domain.model.FlightSegment;
import com.flightaggregation.domain.model.Money;
import com.flightaggregation.domain.model.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.OffsetDateTime;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MockGammaFlightProvider implements FlightSearchProvider {

    private static final Logger log = LoggerFactory.getLogger(MockGammaFlightProvider.class);

    private final URI endpoint;
    private final ProviderHttpClient httpClient;

    public MockGammaFlightProvider(URI endpoint, HttpClient httpClient) {
        this.endpoint = endpoint;
        this.httpClient = new ProviderHttpClient(httpClient);
    }

    @Override
    public Provider provider() {
        return Provider.GAMMA;
    }

    @Override
    public List<FlightItinerary> search(FlightSearchCriteria criteria) {
        return parse(httpClient.search(endpoint, criteria, "Mock-Gamma"));
    }

    private List<FlightItinerary> parse(String response) {
        String trimmed = response.strip();
        if (trimmed.isEmpty()) {
            return List.of();
        }
        Map<String, List<RawSegment>> itinerariesById = new LinkedHashMap<>();
        String[] lines = trimmed.split("\\R");
        for (int index = 0; index < lines.length; index++) {
            if (lines[index].isBlank()) {
                continue;
            }
            try {
                RawSegment segment = parseLine(lines[index], index + 1);
                itinerariesById.computeIfAbsent(segment.itineraryId(), ignored -> new ArrayList<>()).add(segment);
            } catch (ProviderRequestException | IllegalArgumentException | NullPointerException
                     | DateTimeException exception) {
                log.warn("Skipping invalid itinerary record from Mock-Gamma: {}", exception.getMessage());
            }
        }
        List<FlightItinerary> itineraries = new ArrayList<>();
        for (List<RawSegment> segments : itinerariesById.values()) {
            try {
                itineraries.add(toItinerary(segments));
            } catch (IllegalArgumentException | NullPointerException exception) {
                log.warn("Skipping invalid itinerary from Mock-Gamma: {}", exception.getMessage());
            }
        }
        return itineraries;
    }

    private RawSegment parseLine(String line, int lineNumber) {
        String[] fields = line.split(",", -1);
        if (fields.length != 9 && fields.length != 10) {
            throw new ProviderRequestException("Mock-Gamma response has an invalid field count");
        }
        String itineraryId = fields.length == 10 ? fields[9].trim() : "line-" + lineNumber;
        if (itineraryId.isBlank()) {
            throw new IllegalArgumentException("Itinerary ID must not be blank");
        }
        return new RawSegment(
                itineraryId,
                new FlightSegment(
                        new Carrier(fields[0], fields[1]), fields[2], fields[3], fields[4],
                        OffsetDateTime.parse(fields[5]), OffsetDateTime.parse(fields[6])
                ),
                new Money(new BigDecimal(fields[7]), fields[8])
        );
    }

    private FlightItinerary toItinerary(List<RawSegment> rawSegments) {
        Money supplierPrice = rawSegments.getFirst().supplierPrice();
        if (rawSegments.stream().anyMatch(segment -> !supplierPrice.equals(segment.supplierPrice()))) {
            throw new IllegalArgumentException("All segments must have the same supplier price");
        }
        return new FlightItinerary(
                rawSegments.stream().map(RawSegment::segment).toList(),
                supplierPrice,
                provider().name()
        );
    }

    private record RawSegment(String itineraryId, FlightSegment segment, Money supplierPrice) {
    }
}
