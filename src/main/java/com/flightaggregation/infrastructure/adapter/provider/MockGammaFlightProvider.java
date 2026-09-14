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

public final class MockGammaFlightProvider implements FlightProviderRepository {

    private final SimulatedProviderConfig config;

    public MockGammaFlightProvider() {
        this(new SimulatedProviderConfig(Duration.ofSeconds(1), false));
    }

    public MockGammaFlightProvider(SimulatedProviderConfig config) {
        this.config = config;
    }

    @Override
    public ProviderId provider() {
        return ProviderId.MOCK_GAMMA;
    }

    @Override
    public List<FlightItinerary> search(FlightSearchCriteria criteria) {
        simulateRequest();
        return parse(rawResponse(criteria));
    }

    private List<FlightItinerary> parse(String response) {
        String[] fields = response.split(",", -1);
        if (fields.length != 9) {
            throw new ProviderRequestException("Mock-Gamma response has an invalid field count");
        }
        return List.of(new FlightItinerary(
                List.of(new FlightSegment(
                        new Carrier(fields[0], fields[1]), fields[2], fields[3], fields[4],
                        OffsetDateTime.parse(fields[5]), OffsetDateTime.parse(fields[6]))),
                new Money(new BigDecimal(fields[7]), fields[8]),
                provider().name()));
    }

    private String rawResponse(FlightSearchCriteria criteria) {
        String departure = criteria.departureDate().atTime(10, 0).atOffset(ZoneOffset.UTC).toString();
        String arrival = criteria.departureDate().atTime(12, 0).atOffset(ZoneOffset.UTC).toString();
        return String.join(",", "OA", "Omega Air", "OA101", criteria.origin(), criteria.destination(),
                departure, arrival, "118.00", "EUR");
    }

    private void simulateRequest() {
        sleep();
        if (config.failRequests()) {
            throw new ProviderRequestException("Mock-Gamma request failed with status 500");
        }
    }

    private void sleep() {
        try {
            Thread.sleep(config.latency());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ProviderRequestException("Mock-Gamma request was interrupted");
        }
    }
}
