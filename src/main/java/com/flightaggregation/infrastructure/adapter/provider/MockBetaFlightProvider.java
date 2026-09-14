package com.flightaggregation.infrastructure.adapter.provider;

import com.flightaggregation.application.usecase.FlightSearchCriteria;
import com.flightaggregation.application.port.out.FlightSearchProvider;
import com.flightaggregation.domain.model.Carrier;
import com.flightaggregation.domain.model.FlightItinerary;
import com.flightaggregation.domain.model.FlightSegment;
import com.flightaggregation.domain.model.Money;
import com.flightaggregation.domain.model.ProviderId;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.List;

public final class MockBetaFlightProvider implements FlightSearchProvider {

    private final URI endpoint;
    private final ProviderHttpClient httpClient;

    public MockBetaFlightProvider(URI endpoint, HttpClient httpClient) {
        this.endpoint = endpoint;
        this.httpClient = new ProviderHttpClient(httpClient);
    }

    @Override
    public ProviderId provider() {
        return ProviderId.BETA;
    }

    @Override
    public List<FlightItinerary> search(FlightSearchCriteria criteria) {
        return parse(httpClient.search(endpoint, criteria, "Mock-Beta"));
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

}
