package com.flightaggregation.application.usecase;

import com.flightaggregation.infrastructure.adapter.provider.MockAlphaFlightProvider;
import com.flightaggregation.infrastructure.adapter.provider.MockBetaFlightProvider;
import com.flightaggregation.infrastructure.adapter.provider.MockGammaFlightProvider;
import com.flightaggregation.infrastructure.adapter.provider.SimulatedProviderConfig;
import com.flightaggregation.domain.policy.FixedMarkupPolicy;
import com.flightaggregation.domain.policy.MarkupRate;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
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
                            new MockAlphaFlightProvider(fastConfig()),
                            new MockBetaFlightProvider(fastConfig()),
                            new MockGammaFlightProvider(fastConfig())
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
                            new MockAlphaFlightProvider(fastConfig()),
                            new MockBetaFlightProvider(new SimulatedProviderConfig(Duration.ZERO, true))
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
                            new MockAlphaFlightProvider(fastConfig()),
                            new MockGammaFlightProvider(
                                    new SimulatedProviderConfig(Duration.ofMillis(200), false))
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

    private static SimulatedProviderConfig fastConfig() {
        return new SimulatedProviderConfig(Duration.ZERO, false);
    }
}
