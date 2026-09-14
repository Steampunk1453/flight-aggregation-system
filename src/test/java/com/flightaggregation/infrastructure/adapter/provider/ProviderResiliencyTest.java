package com.flightaggregation.infrastructure.adapter.provider;

import com.flightaggregation.application.usecase.FlightSearchCriteria;
import com.flightaggregation.application.usecase.SearchFlightsUseCase;
import com.flightaggregation.domain.policy.FixedMarkupPolicy;
import com.flightaggregation.domain.policy.MarkupRate;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reproduces the resiliency scenario described by the technical assessment: mock provider
 * APIs with artificial latency (up to 5000ms) and occasional 500 errors. The aggregation
 * use case must return partial results instead of failing entirely when a provider is slow
 * or unavailable.
 */
class ProviderResiliencyTest {

    private static final FlightSearchCriteria CRITERIA =
            new FlightSearchCriteria("MAD", "JFK", LocalDate.of(2026, 10, 1));

    @Test
    void returnsPartialResultsWhenOneProviderFailsAndAnotherExceedsTheTimeout() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        try {
            server.start();
            // Alpha: controlled 500, mirroring the "occasional 500 errors" requirement.
            server.createContext("/alpha", exchange -> respond(exchange, 500, ""));
            // Beta: healthy, fast response.
            server.createContext("/beta", exchange -> respond(exchange, 200,
                    "<response><data><flights><flight><carrierCode>OA</carrierCode>"
                            + "<carrierName>Omega Air</carrierName><flightNumber>OA101</flightNumber>"
                            + "<origin>MAD</origin><destination>JFK</destination>"
                            + "<departure>2026-10-01T10:00:00Z</departure>"
                            + "<arrival>2026-10-01T12:00:00Z</arrival>"
                            + "<price>115.00</price><currency>EUR</currency>"
                            + "</flight></flights></data></response>"
            ));
            // Gamma: exceeds the 5000ms documented maximum latency, and must be isolated by
            // the use case's own timeout (well below 5s) rather than blocking the whole search.
            server.createContext("/gamma", exchange -> {
                try {
                    Thread.sleep(5_000);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
                respond(exchange, 200,
                        "OA,Omega Air,OA101,MAD,JFK,2026-10-01T10:00:00Z,2026-10-01T12:00:00Z,118.00,EUR");
            });

            var httpClient = HttpClient.newHttpClient();
            var providers = List.of(
                    new MockAlphaFlightProvider(endpoint(server, "/alpha"), httpClient),
                    new MockBetaFlightProvider(endpoint(server, "/beta"), httpClient),
                    new MockGammaFlightProvider(endpoint(server, "/gamma"), httpClient)
            );

            try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
                var useCase = new SearchFlightsUseCase(
                        providers,
                        Duration.ofSeconds(1),
                        executor,
                        new FixedMarkupPolicy(new MarkupRate(new BigDecimal("5")))
                );

                var result = useCase.search(CRITERIA);

                assertEquals(1, result.itineraries().size());
                assertEquals("BETA", result.itineraries().getFirst().itinerary().provider());
                assertEquals(2, result.failures().size());
                assertTrue(result.failures().stream()
                        .anyMatch(failure -> failure.provider().name().equals("ALPHA")));
                assertTrue(result.failures().stream()
                        .anyMatch(failure -> failure.provider().name().equals("GAMMA")));
            }
        } finally {
            server.stop(0);
        }
    }

    private static URI endpoint(HttpServer server, String path) {
        return URI.create("http://localhost:" + server.getAddress().getPort() + path);
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body)
            throws IOException {
        byte[] response = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }
}
