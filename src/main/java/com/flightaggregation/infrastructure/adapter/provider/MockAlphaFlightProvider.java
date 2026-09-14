package com.flightaggregation.infrastructure.adapter.provider;

import com.flightaggregation.application.usecase.FlightSearchCriteria;
import com.flightaggregation.domain.model.Carrier;
import com.flightaggregation.domain.model.FlightItinerary;
import com.flightaggregation.domain.model.FlightSegment;
import com.flightaggregation.domain.model.Money;
import com.flightaggregation.domain.model.ProviderId;
import com.flightaggregation.domain.repository.FlightProviderRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MockAlphaFlightProvider implements FlightProviderRepository {

    private static final Pattern FLIGHT_PATTERN = Pattern.compile(
            "\\{\"carrier\":\"([^\"]+)\",\"carrierName\":\"([^\"]+)\",\"flightNumber\":\"([^\"]+)\","
                    + "\"origin\":\"([^\"]+)\",\"destination\":\"([^\"]+)\",\"departure\":\"([^\"]+)\","
                    + "\"arrival\":\"([^\"]+)\",\"price\":([0-9.]+),\"currency\":\"([^\"]+)\"\\}");

    private final SimulatedProviderConfig config;

    public MockAlphaFlightProvider() {
        this(new SimulatedProviderConfig(Duration.ofMillis(100), false));
    }

    public MockAlphaFlightProvider(SimulatedProviderConfig config) {
        this.config = config;
    }

    @Override
    public ProviderId provider() {
        return ProviderId.MOCK_ALPHA;
    }

    @Override
    public List<FlightItinerary> search(FlightSearchCriteria criteria) {
        simulateRequest();
        return parse(criteria, rawResponse(criteria));
    }

    private List<FlightItinerary> parse(FlightSearchCriteria criteria, String response) {
        Matcher matcher = FLIGHT_PATTERN.matcher(response);
        List<FlightItinerary> itineraries = new java.util.ArrayList<>();
        while (matcher.find()) {
            itineraries.add(new FlightItinerary(
                    List.of(new FlightSegment(
                            new Carrier(matcher.group(1), matcher.group(2)),
                            matcher.group(3), matcher.group(4), matcher.group(5),
                            OffsetDateTime.parse(matcher.group(6)), OffsetDateTime.parse(matcher.group(7)))),
                    new Money(new BigDecimal(matcher.group(8)), matcher.group(9)),
                    provider().name()));
        }
        return itineraries;
    }

    private String rawResponse(FlightSearchCriteria criteria) {
        String departure = criteria.departureDate().atTime(10, 0).atOffset(ZoneOffset.UTC).toString();
        String arrival = criteria.departureDate().atTime(12, 0).atOffset(ZoneOffset.UTC).toString();
        return "[{\"carrier\":\"OA\",\"carrierName\":\"Omega Air\",\"flightNumber\":\"OA101\","
                + "\"origin\":\"" + criteria.origin() + "\",\"destination\":\"" + criteria.destination() + "\","
                + "\"departure\":\"" + departure + "\",\"arrival\":\"" + arrival
                + "\",\"price\":120.00,\"currency\":\"EUR\"}]";
    }

    private void simulateRequest() {
        sleep();
        if (config.failRequests()) {
            throw new ProviderRequestException("Mock-Alpha request failed with status 500");
        }
    }

    private void sleep() {
        try {
            Thread.sleep(config.latency());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ProviderRequestException("Mock-Alpha request was interrupted");
        }
    }
}
