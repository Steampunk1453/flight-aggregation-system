package com.flightaggregation.application.usecase;

import com.flightaggregation.application.dto.FlightSearchCriteria;
import com.flightaggregation.application.port.out.FlightSearchProvider;
import com.flightaggregation.domain.model.Carrier;
import com.flightaggregation.domain.model.FlightItinerary;
import com.flightaggregation.domain.model.FlightSegment;
import com.flightaggregation.domain.model.Money;
import com.flightaggregation.domain.model.Provider;
import com.flightaggregation.domain.policy.FixedMarkupPolicy;
import com.flightaggregation.domain.policy.MarkupRate;
import org.junit.jupiter.api.DisplayName;
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
    @DisplayName("Queries all providers concurrently and, for duplicate itineraries, keeps only the cheapest one")
    void queriesProvidersConcurrentlyAndKeepsTheCheapestDuplicate() {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var useCase = new SearchFlightsUseCase(
                    List.of(
                            provider(Provider.ALPHA, "120.00", Duration.ZERO, false),
                            provider(Provider.BETA, "115.00", Duration.ZERO, false),
                            provider(Provider.GAMMA, "118.00", Duration.ZERO, false)
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
    @DisplayName("Returns results from providers that work correctly even when another provider fails")
    void returnsHealthyProviderResultsWhenAnotherProviderFails() {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var useCase = new SearchFlightsUseCase(
                    List.of(
                            provider(Provider.ALPHA, "120.00", Duration.ZERO, false),
                            provider(Provider.BETA, "115.00", Duration.ZERO, true)
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
    @DisplayName("Records a slow provider's timeout as a failure without blocking healthy providers' responses")
    void recordsTimeoutWithoutBlockingHealthyProviders() {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var useCase = new SearchFlightsUseCase(
                    List.of(
                            provider(Provider.ALPHA, "120.00", Duration.ZERO, false),
                            provider(Provider.GAMMA, "118.00", Duration.ofMillis(200), false)
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

    @Test
    @DisplayName("Filters deduplicated flights by their selling price after applying the markup")
    void filtersByMaximumSellingPrice() {
        FlightSearchCriteria criteria = new FlightSearchCriteria(
                "MAD",
                "JFK",
                LocalDate.of(2026, 10, 1),
                new BigDecimal("150.00"),
                null
        );
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var useCase = new SearchFlightsUseCase(
                    List.of(
                            provider(Provider.ALPHA, "100.00", "OA", "OA101"),
                            provider(Provider.BETA, "160.00", "AB", "AB201")
                    ),
                    Duration.ofSeconds(1),
                    executor,
                    MARKUP_POLICY
            );

            var result = useCase.search(criteria);

            assertEquals(1, result.itineraries().size());
            assertEquals("OA101", result.itineraries().getFirst().itinerary().segments().getFirst().flightNumber());
            assertEquals(new BigDecimal("105.00"), result.itineraries().getFirst().sellingPrice().amount());
        }
    }

    @Test
    @DisplayName("Filters flights by the requested carrier")
    void filtersByCarrier() {
        FlightSearchCriteria criteria = new FlightSearchCriteria(
                "MAD",
                "JFK",
                LocalDate.of(2026, 10, 1),
                null,
                "AB"
        );
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var useCase = new SearchFlightsUseCase(
                    List.of(
                            provider(Provider.ALPHA, "100.00", "OA", "OA101"),
                            provider(Provider.BETA, "160.00", "AB", "AB201")
                    ),
                    Duration.ofSeconds(1),
                    executor,
                    MARKUP_POLICY
            );

            var result = useCase.search(criteria);

            assertEquals(1, result.itineraries().size());
            assertEquals("AB", result.itineraries().getFirst().itinerary().segments().getFirst().carrier().code());
        }
    }

    private static FlightSearchProvider provider(
            Provider provider,
            String price,
            Duration latency,
            boolean shouldFail
    ) {
        return provider(provider, price, latency, shouldFail, "OA", "OA101");
    }

    private static FlightSearchProvider provider(
            Provider provider,
            String price,
            String carrierCode,
            String flightNumber
    ) {
        return provider(provider, price, Duration.ZERO, false, carrierCode, flightNumber);
    }

    private static FlightSearchProvider provider(
            Provider provider,
            String price,
            Duration latency,
            boolean shouldFail,
            String carrierCode,
            String flightNumber
    ) {
        return new FlightSearchProvider() {
            @Override
            public Provider provider() {
                return provider;
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
                                new Carrier(carrierCode, "Mock Air"),
                                flightNumber,
                                criteria.origin(),
                                criteria.destination(),
                                OffsetDateTime.parse(criteria.departureDate() + "T10:00:00Z"),
                                OffsetDateTime.parse(criteria.departureDate() + "T12:00:00Z")
                        )),
                        new Money(new BigDecimal(price), "EUR"),
                        provider.name()
                ));
            }
        };
    }
}
