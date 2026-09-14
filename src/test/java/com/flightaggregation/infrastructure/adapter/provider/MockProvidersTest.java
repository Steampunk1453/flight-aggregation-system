package com.flightaggregation.infrastructure.adapter.provider;

import com.flightaggregation.application.usecase.FlightSearchCriteria;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MockProvidersTest {

    private static final FlightSearchCriteria CRITERIA =
            new FlightSearchCriteria("MAD", "JFK", LocalDate.of(2026, 10, 1));

    @Test
    void parsesEachLegacyHttpProviderFormatIntoTheDomainModel() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        try {
            server.start();
            server.createContext("/alpha", exchange -> respond(exchange, 200,
                    "[{\"carrier\":\"OA\",\"carrierName\":\"Omega Air\",\"flightNumber\":\"OA101\","
                            + "\"origin\":\"MAD\",\"destination\":\"JFK\","
                            + "\"departure\":\"2026-10-01T10:00:00Z\","
                            + "\"arrival\":\"2026-10-01T12:00:00Z\","
                            + "\"price\":120.00,\"currency\":\"EUR\"}]"
            ));
            server.createContext("/beta", exchange -> respond(exchange, 200,
                    "<response><data><flights><flight><carrierCode>OA</carrierCode>"
                            + "<carrierName>Omega Air</carrierName><flightNumber>OA101</flightNumber>"
                            + "<origin>MAD</origin><destination>JFK</destination>"
                            + "<departure>2026-10-01T10:00:00Z</departure>"
                            + "<arrival>2026-10-01T12:00:00Z</arrival>"
                            + "<price>115.00</price><currency>EUR</currency>"
                            + "</flight></flights></data></response>"
            ));
            server.createContext("/gamma", exchange -> respond(exchange, 200,
                    "OA,Omega Air,OA101,MAD,JFK,2026-10-01T10:00:00Z,"
                            + "2026-10-01T12:00:00Z,118.00,EUR"
            ));

            var httpClient = HttpClient.newHttpClient();
            var providers = List.of(
                    new MockAlphaFlightProvider(endpoint(server, "/alpha"), httpClient),
                    new MockBetaFlightProvider(endpoint(server, "/beta"), httpClient),
                    new MockGammaFlightProvider(endpoint(server, "/gamma"), httpClient)
            );

            providers.forEach(provider -> {
                var itineraries = provider.search(CRITERIA);
                assertEquals(1, itineraries.size());
                assertEquals("MAD", itineraries.getFirst().origin());
                assertEquals("JFK", itineraries.getFirst().destination());
                assertEquals("OA101", itineraries.getFirst().segments().getFirst().flightNumber());
            });
        } finally {
            server.stop(0);
        }
    }

    @Test
    void exposesNonSuccessfulHttpResponsesAsControlledProviderFailures() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        try {
            server.start();
            server.createContext("/flights", exchange -> respond(exchange, 500, ""));
            var provider = new MockAlphaFlightProvider(endpoint(server, "/flights"), HttpClient.newHttpClient());

            assertThrows(ProviderRequestException.class, () -> provider.search(CRITERIA));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void parsesMultiLineCsvStreamsFromMockGamma() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        try {
            server.start();
            server.createContext("/flights", exchange -> respond(exchange, 200,
                    "OA,Omega Air,OA101,MAD,JFK,2026-10-01T10:00:00Z,2026-10-01T12:00:00Z,118.00,EUR\n"
                            + "EF,Echo Fly,EF404,MAD,JFK,2026-10-01T16:00:00Z,2026-10-01T19:30:00Z,142.50,EUR"
            ));
            var provider = new MockGammaFlightProvider(endpoint(server, "/flights"), HttpClient.newHttpClient());

            var itineraries = provider.search(CRITERIA);

            assertEquals(2, itineraries.size());
            assertEquals("OA101", itineraries.get(0).segments().getFirst().flightNumber());
            assertEquals("EF404", itineraries.get(1).segments().getFirst().flightNumber());
        } finally {
            server.stop(0);
        }
    }

    private static URI endpoint(HttpServer server, String path) {
        return URI.create("http://localhost:" + server.getAddress().getPort() + path);
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body)
            throws IOException {
        byte[] response = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }
}
