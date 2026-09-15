package com.flightaggregation.infrastructure;

import com.flightaggregation.application.dto.FlightOffer;
import com.flightaggregation.application.dto.FlightSearchCriteria;
import com.flightaggregation.application.dto.FlightSearchPageRequest;
import com.flightaggregation.application.port.out.FlightSearchResultStore;
import com.flightaggregation.domain.model.Carrier;
import com.flightaggregation.domain.model.FlightItinerary;
import com.flightaggregation.domain.model.FlightSegment;
import com.flightaggregation.domain.model.Money;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("volume")
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class FlightSearchVolumeIT {

    private static final int DEFAULT_ROW_COUNT = 100_000;
    private static final int INSERT_BATCH_SIZE = 1_000;
    private static final int PAGE_SIZE = 100;

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("flight_aggregation")
            .withUsername("flight_aggregation")
            .withPassword("flight_aggregation");

    @Autowired
    private FlightSearchResultStore resultStore;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("flight.search.cache.enabled", () -> false);
    }

    @Test
    void materializesAndTraversesTheConfiguredInventoryVolumeWithKeysetPagination() {
        int rowCount = Integer.getInteger("flight.volume.rows", DEFAULT_ROW_COUNT);
        assertTrue(rowCount >= DEFAULT_ROW_COUNT, "The volume profile must exercise at least 100,000 offers");

        for (int firstRow = 0; firstRow < rowCount; firstRow += INSERT_BATCH_SIZE) {
            int lastRow = Math.min(firstRow + INSERT_BATCH_SIZE, rowCount);
            List<FlightOffer> batch = new ArrayList<>(lastRow - firstRow);
            for (int row = firstRow; row < lastRow; row++) {
                batch.add(flight(row));
            }
            resultStore.upsertAll(batch);
        }

        FlightSearchCriteria criteria = new FlightSearchCriteria(
                "MAD",
                "JFK",
                LocalDate.of(2026, 10, 1)
        );
        var firstPage = resultStore.findPage(criteria, new FlightSearchPageRequest(PAGE_SIZE, null));
        var secondPage = resultStore.findPage(
                criteria,
                new FlightSearchPageRequest(PAGE_SIZE, firstPage.nextCursor())
        );

        assertEquals(PAGE_SIZE, firstPage.flights().size());
        assertEquals(PAGE_SIZE, secondPage.flights().size());
        assertTrue(firstPage.nextCursor() != null);
        assertFalse(secondPage.nextCursor() == null);
        Set<String> firstPageKeys = firstPage.flights().stream()
                .map(offer -> offer.itinerary().deduplicationKey())
                .collect(java.util.stream.Collectors.toCollection(HashSet::new));
        assertTrue(secondPage.flights().stream()
                .map(offer -> offer.itinerary().deduplicationKey())
                .noneMatch(firstPageKeys::contains));
    }

    private static FlightOffer flight(int row) {
        OffsetDateTime departureAt = LocalDate.of(2026, 10, 1)
                .atStartOfDay()
                .atOffset(ZoneOffset.UTC)
                .plusSeconds(row % 86_400);
        Money supplierPrice = new Money(
                BigDecimal.valueOf(10_000L + row % 10_000L, 2),
                "EUR"
        );
        return new FlightOffer(
                new FlightItinerary(
                        List.of(new FlightSegment(
                                new Carrier("HV", "High Volume Air"),
                                "HV" + row,
                                "MAD",
                                "JFK",
                                departureAt,
                                departureAt.plusHours(2)
                        )),
                        supplierPrice,
                        "VOLUME"
                ),
                supplierPrice
        );
    }
}
