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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MockAlphaFlightProvider implements FlightSearchProvider {

    private static final Pattern FLIGHT_PATTERN = Pattern.compile(
            "\\{\"carrier\":\"([^\"]+)\",\"carrierName\":\"([^\"]+)\",\"flightNumber\":\"([^\"]+)\","
                    + "\"origin\":\"([^\"]+)\",\"destination\":\"([^\"]+)\",\"departure\":\"([^\"]+)\","
                    + "\"arrival\":\"([^\"]+)\",\"price\":([0-9.]+),\"currency\":\"([^\"]+)\"}");

    private final URI endpoint;
    private final ProviderHttpClient httpClient;

    public MockAlphaFlightProvider(URI endpoint, HttpClient httpClient) {
        this.endpoint = endpoint;
        this.httpClient = new ProviderHttpClient(httpClient);
    }

    @Override
    public ProviderId provider() {
        return ProviderId.ALPHA;
    }

    @Override
    public List<FlightItinerary> search(FlightSearchCriteria criteria) {
        return parse(httpClient.search(endpoint, criteria, "Mock-Alpha"));
    }

    private List<FlightItinerary> parse(String response) {
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

}
