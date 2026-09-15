package com.flightaggregation.infrastructure.adapter.provider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.List;

public final class MockAlphaFlightProvider implements FlightSearchProvider {

    private static final Logger log = LoggerFactory.getLogger(MockAlphaFlightProvider.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final URI endpoint;
    private final ProviderHttpClient httpClient;

    public MockAlphaFlightProvider(URI endpoint, HttpClient httpClient) {
        this.endpoint = endpoint;
        this.httpClient = new ProviderHttpClient(httpClient);
    }

    @Override
    public Provider provider() {
        return Provider.ALPHA;
    }

    @Override
    public List<FlightItinerary> search(FlightSearchCriteria criteria) {
        return parse(httpClient.search(endpoint, criteria, "Mock-Alpha"));
    }

    private List<FlightItinerary> parse(String response) {
        JsonNode flights;
        try {
            flights = objectMapper.readTree(response);
        } catch (JsonProcessingException exception) {
            throw new ProviderRequestException("Mock-Alpha response could not be parsed");
        }
        if (flights == null || !flights.isArray()) {
            throw new ProviderRequestException("Mock-Alpha response must be a JSON array");
        }
        List<FlightItinerary> itineraries = new ArrayList<>();
        for (JsonNode flight : flights) {
            try {
                itineraries.add(parseItinerary(flight));
            } catch (IllegalArgumentException | NullPointerException | DateTimeException exception) {
                log.warn("Skipping invalid itinerary from Mock-Alpha: {}", exception.getMessage());
            }
        }
        return itineraries;
    }

    private FlightItinerary parseItinerary(JsonNode flight) {
        JsonNode segments = flight.get("segments");
        List<FlightSegment> parsedSegments;
        if (segments == null) {
            parsedSegments = List.of(parseSegment(flight));
        } else {
            if (!segments.isArray() || segments.isEmpty()) {
                throw new IllegalArgumentException("An itinerary must contain at least one segment");
            }
            parsedSegments = new ArrayList<>();
            for (JsonNode segment : segments) {
                parsedSegments.add(parseSegment(segment));
            }
        }
        return new FlightItinerary(
                parsedSegments,
                new Money(decimal(flight, "price"), text(flight, "currency")),
                provider().name()
        );
    }

    private static FlightSegment parseSegment(JsonNode segment) {
        return new FlightSegment(
                new Carrier(text(segment, "carrier"), text(segment, "carrierName")),
                text(segment, "flightNumber"),
                text(segment, "origin"),
                text(segment, "destination"),
                OffsetDateTime.parse(text(segment, "departure")),
                OffsetDateTime.parse(text(segment, "arrival"))
        );
    }

    private static String text(JsonNode object, String field) {
        JsonNode value = object.get(field);
        if (value == null || value.isNull() || value.asText().isBlank()) {
            throw new IllegalArgumentException("Missing " + field);
        }
        return value.asText();
    }

    private static BigDecimal decimal(JsonNode object, String field) {
        return new BigDecimal(text(object, field));
    }
}
