package com.flightaggregation.infrastructure.adapter.provider;

import com.flightaggregation.application.usecase.FlightSearchCriteria;
import com.flightaggregation.application.port.out.FlightSearchProvider;
import com.flightaggregation.domain.model.Carrier;
import com.flightaggregation.domain.model.FlightItinerary;
import com.flightaggregation.domain.model.FlightSegment;
import com.flightaggregation.domain.model.Money;
import com.flightaggregation.domain.model.ProviderId;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.List;

public final class MockGammaFlightProvider implements FlightSearchProvider {

    private final URI endpoint;
    private final ProviderHttpClient httpClient;

    public MockGammaFlightProvider(URI endpoint, HttpClient httpClient) {
        this.endpoint = endpoint;
        this.httpClient = new ProviderHttpClient(httpClient);
    }

    @Override
    public ProviderId provider() {
        return ProviderId.GAMMA;
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
        return trimmed.lines()
                .filter(line -> !line.isBlank())
                .map(this::parseLine)
                .toList();
    }

    private FlightItinerary parseLine(String line) {
        String[] fields = line.split(",", -1);
        if (fields.length != 9) {
            throw new ProviderRequestException("Mock-Gamma response has an invalid field count");
        }
        return new FlightItinerary(
                List.of(new FlightSegment(
                        new Carrier(fields[0], fields[1]), fields[2], fields[3], fields[4],
                        OffsetDateTime.parse(fields[5]), OffsetDateTime.parse(fields[6]))),
                new Money(new BigDecimal(fields[7]), fields[8]),
                provider().name());
    }

}
