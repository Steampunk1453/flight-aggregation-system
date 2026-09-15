package com.flightaggregation.infrastructure.adapter.provider;

import com.flightaggregation.application.dto.FlightSearchCriteria;
import com.flightaggregation.application.port.out.FlightSearchProvider;
import com.flightaggregation.domain.model.Carrier;
import com.flightaggregation.domain.model.FlightItinerary;
import com.flightaggregation.domain.model.FlightSegment;
import com.flightaggregation.domain.model.Money;
import com.flightaggregation.domain.model.Provider;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.OffsetDateTime;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import org.xml.sax.SAXException;

public final class MockBetaFlightProvider implements FlightSearchProvider {

    private static final Logger log = LoggerFactory.getLogger(MockBetaFlightProvider.class);

    private final URI endpoint;
    private final ProviderHttpClient httpClient;

    public MockBetaFlightProvider(URI endpoint, HttpClient httpClient) {
        this.endpoint = endpoint;
        this.httpClient = new ProviderHttpClient(httpClient);
    }

    @Override
    public Provider provider() {
        return Provider.BETA;
    }

    @Override
    public List<FlightItinerary> search(FlightSearchCriteria criteria) {
        return parse(httpClient.search(endpoint, criteria, "Mock-Beta"));
    }

    private List<FlightItinerary> parse(String response) {
        try {
            var document = documentBuilderFactory()
                    .newDocumentBuilder()
                    .parse(new ByteArrayInputStream(response.getBytes(StandardCharsets.UTF_8)));
            var flights = document.getElementsByTagName("flight");
            List<FlightItinerary> itineraries = new ArrayList<>();
            for (int index = 0; index < flights.getLength(); index++) {
                Element flight = (Element) flights.item(index);
                try {
                    itineraries.add(parseItinerary(flight));
                } catch (IllegalArgumentException | NullPointerException | DateTimeException exception) {
                    log.warn("Skipping invalid itinerary from Mock-Beta: {}", exception.getMessage());
                }
            }
            return itineraries;
        } catch (ParserConfigurationException | SAXException | IOException exception) {
            throw new ProviderRequestException("Mock-Beta response could not be parsed");
        }
    }

    private FlightItinerary parseItinerary(Element flight) {
        Element segmentContainer = child(flight, "segments");
        List<FlightSegment> segments = segmentContainer == null
                ? List.of(parseSegment(flight))
                : elements(segmentContainer, "segment").stream().map(MockBetaFlightProvider::parseSegment).toList();
        if (segments.isEmpty()) {
            throw new IllegalArgumentException("An itinerary must contain at least one segment");
        }
        return new FlightItinerary(
                segments,
                new Money(new BigDecimal(text(flight, "price")), text(flight, "currency")),
                provider().name()
        );
    }

    private static DocumentBuilderFactory documentBuilderFactory() throws ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory;
    }

    private static FlightSegment parseSegment(Element segment) {
        return new FlightSegment(
                new Carrier(text(segment, "carrierCode"), text(segment, "carrierName")),
                text(segment, "flightNumber"),
                text(segment, "origin"),
                text(segment, "destination"),
                OffsetDateTime.parse(text(segment, "departure")),
                OffsetDateTime.parse(text(segment, "arrival"))
        );
    }

    private static Element child(Element parent, String tag) {
        NodeList children = parent.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            if (children.item(index) instanceof Element element && element.getTagName().equals(tag)) {
                return element;
            }
        }
        return null;
    }

    private static List<Element> elements(Element parent, String tag) {
        NodeList nodes = parent.getElementsByTagName(tag);
        List<Element> elements = new ArrayList<>();
        for (int index = 0; index < nodes.getLength(); index++) {
            elements.add((Element) nodes.item(index));
        }
        return elements;
    }

    private static String text(Element parent, String tag) {
        Element element = child(parent, tag);
        if (element == null || element.getTextContent().isBlank()) {
            throw new IllegalArgumentException("Missing " + tag);
        }
        return element.getTextContent();
    }

}
