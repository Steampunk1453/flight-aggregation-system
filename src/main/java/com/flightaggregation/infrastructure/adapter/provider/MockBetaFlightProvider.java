package com.flightaggregation.infrastructure.adapter.provider;

import com.flightaggregation.application.usecase.FlightSearchCriteria;
import com.flightaggregation.domain.model.Carrier;
import com.flightaggregation.domain.model.FlightItinerary;
import com.flightaggregation.domain.model.FlightSegment;
import com.flightaggregation.domain.model.Money;
import com.flightaggregation.domain.model.ProviderId;
import com.flightaggregation.domain.repository.FlightProviderRepository;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public final class MockBetaFlightProvider implements FlightProviderRepository {

    private final SimulatedProviderConfig config;

    public MockBetaFlightProvider() {
        this(new SimulatedProviderConfig(Duration.ofMillis(500), false));
    }

    public MockBetaFlightProvider(SimulatedProviderConfig config) {
        this.config = config;
    }

    @Override
    public ProviderId provider() {
        return ProviderId.MOCK_BETA;
    }

    @Override
    public List<FlightItinerary> search(FlightSearchCriteria criteria) {
        simulateRequest();
        return parse(rawResponse(criteria));
    }

    private List<FlightItinerary> parse(String response) {
        try {
            var document = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(new ByteArrayInputStream(response.getBytes(StandardCharsets.UTF_8)));
            var flights = document.getElementsByTagName("flight");
            List<FlightItinerary> itineraries = new ArrayList<>();
            for (int index = 0; index < flights.getLength(); index++) {
                Element flight = (Element) flights.item(index);
                itineraries.add(new FlightItinerary(
                        List.of(new FlightSegment(
                                new Carrier(text(flight, "carrierCode"), text(flight, "carrierName")),
                                text(flight, "flightNumber"), text(flight, "origin"), text(flight, "destination"),
                                OffsetDateTime.parse(text(flight, "departure")),
                                OffsetDateTime.parse(text(flight, "arrival")))),
                        new Money(new BigDecimal(text(flight, "price")), text(flight, "currency")),
                        provider().name()));
            }
            return itineraries;
        } catch (Exception exception) {
            throw new ProviderRequestException("Mock-Beta response could not be parsed");
        }
    }

    private String text(Element parent, String tag) {
        return ((Element) parent.getElementsByTagName(tag).item(0)).getTextContent();
    }

    private String rawResponse(FlightSearchCriteria criteria) {
        String departure = criteria.departureDate().atTime(10, 0).atOffset(ZoneOffset.UTC).toString();
        String arrival = criteria.departureDate().atTime(12, 0).atOffset(ZoneOffset.UTC).toString();
        return "<response><data><flights><flight><carrierCode>OA</carrierCode>"
                + "<carrierName>Omega Air</carrierName><flightNumber>OA101</flightNumber><origin>"
                + criteria.origin() + "</origin><destination>" + criteria.destination() + "</destination><departure>"
                + departure + "</departure><arrival>" + arrival + "</arrival><price>115.00</price>"
                + "<currency>EUR</currency></flight></flights></data></response>";
    }

    private void simulateRequest() {
        sleep();
        if (config.failRequests()) {
            throw new ProviderRequestException("Mock-Beta request failed with status 500");
        }
    }

    private void sleep() {
        try {
            Thread.sleep(config.latency());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ProviderRequestException("Mock-Beta request was interrupted");
        }
    }
}
