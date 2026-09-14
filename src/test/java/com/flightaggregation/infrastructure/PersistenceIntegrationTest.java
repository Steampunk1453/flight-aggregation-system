package com.flightaggregation.infrastructure;

import com.flightaggregation.application.port.out.FlightSearchResultStore;
import com.flightaggregation.application.usecase.FlightSearchCriteria;
import com.flightaggregation.application.usecase.FlightSearchPageRequest;
import com.flightaggregation.application.usecase.SearchFlight;
import com.flightaggregation.domain.model.Carrier;
import com.flightaggregation.domain.model.FlightItinerary;
import com.flightaggregation.domain.model.FlightSegment;
import com.flightaggregation.domain.model.Money;
import com.flightaggregation.infrastructure.adapter.cache.RedisFlightSearchCache;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class PersistenceIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("flight_aggregation")
            .withUsername("flight_aggregation")
            .withPassword("flight_aggregation");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    @Autowired
    private RedisFlightSearchCache cache;

    @Autowired
    private FlightSearchResultStore resultStore;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Test
    void storesSearchResultsAndReadsThemWithKeysetPagination() {
        FlightSearchCriteria criteria = new FlightSearchCriteria(
                "MAD",
                "JFK",
                LocalDate.of(2026, 10, 1),
                new BigDecimal("250.00"),
                "OA"
        );
        SearchFlight firstFlight = flight("OA101", "2026-10-01T10:00:00Z", "120.00");
        SearchFlight secondFlight = flight("OA102", "2026-10-01T11:00:00Z", "125.00");

        cache.put(criteria, new com.flightaggregation.application.usecase.FlightSearchResult(
                List.of(firstFlight),
                List.of()
        ));
        assertEquals(1, cache.find(criteria).orElseThrow().itineraries().size());
        assertTrue(cache.find(new FlightSearchCriteria(
                "MAD",
                "JFK",
                LocalDate.of(2026, 10, 1),
                new BigDecimal("200.00"),
                "OA"
        )).isEmpty());

        resultStore.upsertAll(List.of(firstFlight, secondFlight));
        var firstPage = resultStore.findPage(criteria, new FlightSearchPageRequest(1, null));

        assertEquals("OA101", firstPage.flights().getFirst().itinerary().segments().getFirst().flightNumber());
        assertNotNull(firstPage.nextCursor());

        var secondPage = resultStore.findPage(
                criteria,
                new FlightSearchPageRequest(1, firstPage.nextCursor())
        );

        assertEquals("OA102", secondPage.flights().getFirst().itinerary().segments().getFirst().flightNumber());
        assertTrue(secondPage.nextCursor() == null);
    }

    private static SearchFlight flight(String flightNumber, String departureAt, String price) {
        return new SearchFlight(
                new FlightItinerary(
                        List.of(new FlightSegment(
                                new Carrier("OA", "Omega Air"),
                                flightNumber,
                                "MAD",
                                "JFK",
                                OffsetDateTime.parse(departureAt),
                                OffsetDateTime.parse(departureAt).plusHours(2)
                        )),
                        new Money(new BigDecimal(price), "EUR"),
                        "BETA"
                ),
                new Money(new BigDecimal(price), "EUR")
        );
    }
}
