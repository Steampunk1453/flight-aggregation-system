package com.flightaggregation.application.usecase;

import com.flightaggregation.application.port.out.FlightSearchProvider;
import com.flightaggregation.domain.model.Carrier;
import com.flightaggregation.domain.model.FlightItinerary;
import com.flightaggregation.domain.model.FlightSegment;
import com.flightaggregation.domain.model.Money;
import com.flightaggregation.domain.model.ProviderId;
import com.flightaggregation.domain.policy.FixedMarkupPolicy;
import com.flightaggregation.domain.policy.MarkupRate;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SearchFlightsUseCaseTest {

    private static final FlightSearchCriteria CRITERIA =
            new FlightSearchCriteria("MAD", "JFK", LocalDate.of(2026, 10, 1));
    private static final FixedMarkupPolicy MARKUP_POLICY =
            new FixedMarkupPolicy(new MarkupRate(new BigDecimal("5")));

    @Test
    void queriesProvidersConcurrentlyAndKeepsTheCheapestDuplicate() {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var useCase = new SearchFlightsUseCase(
                    List.of(
                            provider(ProviderId.ALPHA, "120.00", Duration.ZERO, false),
                            provider(ProviderId.BETA, "115.00", Duration.ZERO, false),
                            provider(ProviderId.GAMMA, "118.00", Duration.ZERO, false)
                    ),
                    Duration.ofSeconds(1),
                    executor,
                    MARKUP_POLICY
            );

            var result = useCase.search(CRITERIA);

            assertEquals(1, result.itineraries().size());
            assertEquals("BETA", result.itineraries().getFirst().itinerary().provider());
            assertEquals(new BigDecimal("115.00"),
                    result.itineraries().getFirst().itinerary().supplierPrice().amount());
            assertEquals(new BigDecimal("120.75"),
                    result.itineraries().getFirst().sellingPrice().amount());
            assertEquals(0, result.failures().size());
        }
    }

    @Test
    void returnsHealthyProviderResultsWhenAnotherProviderFails() {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var useCase = new SearchFlightsUseCase(
                    List.of(
                            provider(ProviderId.ALPHA, "120.00", Duration.ZERO, false),
                            provider(ProviderId.BETA, "115.00", Duration.ZERO, true)
                    ),
                    Duration.ofSeconds(1),
                    executor,
                    MARKUP_POLICY
            );

            var result = useCase.search(CRITERIA);

            assertEquals(1, result.itineraries().size());
            assertEquals(1, result.failures().size());
            assertEquals("BETA", result.failures().getFirst().provider().name());
        }
    }

    @Test
    void recordsTimeoutWithoutBlockingHealthyProviders() {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var useCase = new SearchFlightsUseCase(
                    List.of(
                            provider(ProviderId.ALPHA, "120.00", Duration.ZERO, false),
                            provider(ProviderId.GAMMA, "118.00", Duration.ofMillis(200), false)
                    ),
                    Duration.ofMillis(25),
                    executor,
                    MARKUP_POLICY
            );

            var result = useCase.search(CRITERIA);

            assertEquals(1, result.itineraries().size());
            assertEquals(1, result.failures().size());
            assertEquals("GAMMA", result.failures().getFirst().provider().name());
        }
    }

    private static FlightSearchProvider provider(
            ProviderId providerId,
            String price,
            Duration latency,
            boolean shouldFail
    ) {
        return new FlightSearchProvider() {
            @Override
            public ProviderId provider() {
                return providerId;
            }

            @Override
            public List<FlightItinerary> search(FlightSearchCriteria criteria) {
                try {
                    Thread.sleep(latency);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
                if (shouldFail) {
                    throw new IllegalStateException("Provider unavailable");
                }
                return List.of(new FlightItinerary(
                        List.of(new FlightSegment(
                                new Carrier("OA", "Omega Air"),
                                "OA101",
                                criteria.origin(),
                                criteria.destination(),
                                OffsetDateTime.parse(criteria.departureDate() + "T10:00:00Z"),
                                OffsetDateTime.parse(criteria.departureDate() + "T12:00:00Z")
                        )),
                        new Money(new BigDecimal(price), "EUR"),
                        providerId.name()
                ));
            }
        };
    }
}
