package com.flightaggregation.infrastructure.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flightaggregation.infrastructure.adapter.persistence.jpa.MaterializedFlightJpaRepository;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class FlightSearchControllerIT {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final ProviderServer providerServer = ProviderServer.start();

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("flight_aggregation")
            .withUsername("flight_aggregation")
            .withPassword("flight_aggregation");

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MaterializedFlightJpaRepository repository;

    @Autowired
    private RedisConnectionFactory redisConnectionFactory;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("flight.providers.alpha.url", () -> providerServer.endpoint("/alpha").toString());
        registry.add("flight.providers.beta.url", () -> providerServer.endpoint("/beta").toString());
        registry.add("flight.providers.gamma.url", () -> providerServer.endpoint("/gamma").toString());
    }

    @BeforeEach
    void clearState() {
        repository.deleteAll();
        try (var connection = redisConnectionFactory.getConnection()) {
            connection.serverCommands().flushAll();
        }
        providerServer.reset();
    }

    @AfterAll
    static void stopProviderServer() {
        providerServer.close();
    }

    @Test
    @DisplayName("Executes the complete HTTP, provider, cache, persistence, and keyset pagination flow")
    void returnsDeduplicatedPagesFromTheLiveProviderSearch() throws Exception {
        var firstPage = mockMvc.perform(get("/api/flights/search")
                        .queryParam("origin", "MAD")
                        .queryParam("destination", "JFK")
                        .queryParam("departureDate", "2026-10-01")
                        .queryParam("pageSize", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flights.length()").value(2))
                .andExpect(jsonPath("$.flights[0].segments[0].flightNumber").value("AL101"))
                .andExpect(jsonPath("$.flights[1].provider").value("BETA"))
                .andExpect(jsonPath("$.flights[1].supplierPrice.amount").value(115.00))
                .andExpect(jsonPath("$.flights[1].sellingPrice.amount").value(120.75))
                .andExpect(jsonPath("$.providerFailures.length()").value(0))
                .andExpect(jsonPath("$.nextCursor").isNotEmpty())
                .andReturn();
        JsonNode firstPayload = objectMapper.readTree(firstPage.getResponse().getContentAsString());
        String cursor = firstPayload.path("nextCursor").asText();

        mockMvc.perform(get("/api/flights/search")
                        .queryParam("origin", "MAD")
                        .queryParam("destination", "JFK")
                        .queryParam("departureDate", "2026-10-01")
                        .queryParam("pageSize", "2")
                        .queryParam("cursor", cursor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flights.length()").value(2))
                .andExpect(jsonPath("$.flights[0].segments[0].flightNumber").value("EF404"))
                .andExpect(jsonPath("$.flights[1].segments[0].flightNumber").value("CD301"))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
    }

    @Test
    @DisplayName("Filters an itinerary by a carrier appearing on its connecting segment")
    void filtersByCarrierOnAConnectingSegment() throws Exception {
        mockMvc.perform(get("/api/flights/search")
                        .queryParam("origin", "MAD")
                        .queryParam("destination", "JFK")
                        .queryParam("departureDate", "2026-10-01")
                        .queryParam("carrier", "ZZ"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flights.length()").value(1))
                .andExpect(jsonPath("$.flights[0].segments.length()").value(2))
                .andExpect(jsonPath("$.flights[0].segments[1].carrierCode").value("ZZ"));
    }

    @Test
    @DisplayName("Caches identical searches and invalidates the cache key when filters change")
    void cachesSearchesUsingTheCompleteCriteria() throws Exception {
        performSearch("200");
        performSearch("200");

        assertEquals(1, providerServer.alphaRequests());
        assertEquals(1, providerServer.betaRequests());
        assertEquals(1, providerServer.gammaRequests());

        performSearch("201");

        assertEquals(2, providerServer.alphaRequests());
        assertEquals(2, providerServer.betaRequests());
        assertEquals(2, providerServer.gammaRequests());
    }

    @Test
    @DisplayName("Returns partial results when Alpha fails and Gamma exceeds the configured timeout")
    void returnsPartialResultsWhenProvidersFailOrTimeout() throws Exception {
        mockMvc.perform(get("/api/flights/search")
                        .queryParam("origin", "ERR")
                        .queryParam("destination", "JFK")
                        .queryParam("departureDate", "2026-10-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flights.length()").value(2))
                .andExpect(jsonPath("$.providerFailures.length()").value(2))
                .andExpect(jsonPath("$.providerFailures[0].provider").value("ALPHA"))
                .andExpect(jsonPath("$.providerFailures[1].provider").value("GAMMA"));
    }

    private void performSearch(String maxPrice) throws Exception {
        mockMvc.perform(get("/api/flights/search")
                        .queryParam("origin", "MAD")
                        .queryParam("destination", "JFK")
                        .queryParam("departureDate", "2026-10-01")
                        .queryParam("maxPrice", maxPrice))
                .andExpect(status().isOk());
    }

    private static final class ProviderServer implements AutoCloseable {

        private final HttpServer server;
        private final ExecutorService executor;
        private final AtomicInteger alphaRequests = new AtomicInteger();
        private final AtomicInteger betaRequests = new AtomicInteger();
        private final AtomicInteger gammaRequests = new AtomicInteger();

        private ProviderServer(HttpServer server, ExecutorService executor) {
            this.server = server;
            this.executor = executor;
        }

        static ProviderServer start() {
            try {
                HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
                ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
                ProviderServer providerServer = new ProviderServer(server, executor);
                server.setExecutor(executor);
                server.createContext("/alpha", providerServer::handleAlpha);
                server.createContext("/beta", providerServer::handleBeta);
                server.createContext("/gamma", providerServer::handleGamma);
                server.start();
                return providerServer;
            } catch (IOException exception) {
                throw new IllegalStateException("Could not start the test provider server", exception);
            }
        }

        URI endpoint(String path) {
            return URI.create("http://localhost:" + server.getAddress().getPort() + path);
        }

        void reset() {
            alphaRequests.set(0);
            betaRequests.set(0);
            gammaRequests.set(0);
        }

        int alphaRequests() {
            return alphaRequests.get();
        }

        int betaRequests() {
            return betaRequests.get();
        }

        int gammaRequests() {
            return gammaRequests.get();
        }

        private void handleAlpha(HttpExchange exchange) throws IOException {
            alphaRequests.incrementAndGet();
            if (queryParameter(exchange, "origin").equals("ERR")) {
                respond(exchange, 500, "");
                return;
            }
            respond(exchange, 200, alphaResponse(exchange));
        }

        private void handleBeta(HttpExchange exchange) throws IOException {
            betaRequests.incrementAndGet();
            respond(exchange, 200, betaResponse(exchange));
        }

        private void handleGamma(HttpExchange exchange) throws IOException {
            gammaRequests.incrementAndGet();
            if (queryParameter(exchange, "origin").equals("ERR")) {
                try {
                    Thread.sleep(5_000);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            respond(exchange, 200, gammaResponse(exchange));
        }

        @Override
        public void close() {
            server.stop(0);
            executor.close();
        }
    }

    private static String alphaResponse(HttpExchange exchange) {
        return """
                [{"price":200.00,"currency":"EUR","segments":[
                {"carrier":"AL","carrierName":"Alpha Air","flightNumber":"AL101","origin":"%s","destination":"LHR","departure":"%sT08:00:00Z","arrival":"%sT10:00:00Z"},
                {"carrier":"ZZ","carrierName":"Zulu Air","flightNumber":"ZZ202","origin":"LHR","destination":"%s","departure":"%sT11:00:00Z","arrival":"%sT14:00:00Z"}]},
                {"carrier":"OA","carrierName":"Omega Air","flightNumber":"OA101","origin":"%s","destination":"%s","departure":"%sT10:00:00Z","arrival":"%sT12:00:00Z","price":120.00,"currency":"EUR"}]
                """.formatted(
                queryParameter(exchange, "origin"),
                queryParameter(exchange, "departureDate"),
                queryParameter(exchange, "departureDate"),
                queryParameter(exchange, "destination"),
                queryParameter(exchange, "departureDate"),
                queryParameter(exchange, "departureDate"),
                queryParameter(exchange, "origin"),
                queryParameter(exchange, "destination"),
                queryParameter(exchange, "departureDate"),
                queryParameter(exchange, "departureDate")
        );
    }

    private static String betaResponse(HttpExchange exchange) {
        return """
                <response><data><flights>
                <flight><carrierCode>OA</carrierCode><carrierName>Omega Air</carrierName><flightNumber>OA101</flightNumber><origin>%s</origin><destination>%s</destination><departure>%sT10:00:00Z</departure><arrival>%sT12:00:00Z</arrival><price>115.00</price><currency>EUR</currency></flight>
                <flight><carrierCode>CD</carrierCode><carrierName>Continental Direct</carrierName><flightNumber>CD301</flightNumber><origin>%s</origin><destination>%s</destination><departure>%sT14:00:00Z</departure><arrival>%sT17:00:00Z</arrival><price>130.00</price><currency>EUR</currency></flight>
                </flights></data></response>
                """.formatted(
                queryParameter(exchange, "origin"),
                queryParameter(exchange, "destination"),
                queryParameter(exchange, "departureDate"),
                queryParameter(exchange, "departureDate"),
                queryParameter(exchange, "origin"),
                queryParameter(exchange, "destination"),
                queryParameter(exchange, "departureDate"),
                queryParameter(exchange, "departureDate")
        );
    }

    private static String gammaResponse(HttpExchange exchange) {
        return "EF,Echo Fly,EF404,%s,%s,%sT12:00:00Z,%sT15:00:00Z,140.00,EUR".formatted(
                queryParameter(exchange, "origin"),
                queryParameter(exchange, "destination"),
                queryParameter(exchange, "departureDate"),
                queryParameter(exchange, "departureDate")
        );
    }

    private static String queryParameter(HttpExchange exchange, String name) {
        return List.of(exchange.getRequestURI().getRawQuery().split("&")).stream()
                .map(parameter -> parameter.split("=", 2))
                .filter(parameter -> parameter[0].equals(name))
                .map(parameter -> parameter.length == 2
                        ? URLDecoder.decode(parameter[1], StandardCharsets.UTF_8)
                        : "")
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Missing query parameter: " + name));
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] response = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }
}
