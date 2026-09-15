package com.flightaggregation.application.usecase;

import com.flightaggregation.application.dto.FlightOffer;
import com.flightaggregation.application.dto.FlightSearchCriteria;
import com.flightaggregation.application.dto.FlightSearchResult;
import com.flightaggregation.application.dto.ProviderSearchFailure;
import com.flightaggregation.domain.model.FlightItinerary;
import com.flightaggregation.application.port.in.SearchFlights;
import com.flightaggregation.application.port.out.FlightSearchProvider;
import com.flightaggregation.application.port.out.ProviderFailureException;
import com.flightaggregation.domain.policy.MarkupPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class SearchFlightsUseCase implements SearchFlights {

    private static final Logger log = LoggerFactory.getLogger(SearchFlightsUseCase.class);

    private final List<FlightSearchProvider> providers;
    private final Duration providerTimeout;
    private final ExecutorService executor;
    private final MarkupPolicy markupPolicy;

    public SearchFlightsUseCase(
            Collection<FlightSearchProvider> providers,
            Duration providerTimeout,
            ExecutorService executor,
            MarkupPolicy markupPolicy
    ) {
        Objects.requireNonNull(providers, "Providers must not be null");
        Objects.requireNonNull(providerTimeout, "Provider timeout must not be null");
        Objects.requireNonNull(executor, "Executor must not be null");
        Objects.requireNonNull(markupPolicy, "Markup policy must not be null");
        if (providers.isEmpty()) {
            throw new IllegalArgumentException("At least one flight provider is required");
        }
        if (providerTimeout.isNegative() || providerTimeout.isZero()) {
            throw new IllegalArgumentException("Provider timeout must be positive");
        }
        this.providers = List.copyOf(providers);
        this.providerTimeout = providerTimeout;
        this.executor = executor;
        this.markupPolicy = markupPolicy;
    }

    @Override
    public FlightSearchResult search(FlightSearchCriteria criteria) {
        Objects.requireNonNull(criteria, "Search criteria must not be null");
        log.debug(
                "Querying {} providers for {}->{} on {}",
                providers.size(), criteria.origin(), criteria.destination(), criteria.departureDate()
        );
        List<CompletableFuture<ProviderResult>> requests = providers.stream()
                .map(provider -> request(provider, criteria))
                .toList();

        List<ProviderResult> results = requests.stream()
                .map(CompletableFuture::join)
                .toList();

        List<FlightOffer> itineraries = results.stream()
                    .flatMap(result -> result.itineraries().stream())
                    .collect(Collectors.collectingAndThen(
                            Collectors.toMap(
                                    FlightItinerary::deduplicationKey,
                                    Function.identity(),
                                    SearchFlightsUseCase::cheapest
                            ),
                            values -> values.values().stream()
                                    .sorted(java.util.Comparator.comparing(FlightItinerary::departureAt))
                                    .map(itinerary -> new FlightOffer(
                                            itinerary,
                                            markupPolicy.sellingPriceFor(itinerary)
                                    ))
                                    .filter(flight -> matches(criteria, flight))
                                    .toList()
                    ));

            List<ProviderSearchFailure> failures = results.stream()
                    .map(ProviderResult::failure)
                    .filter(Objects::nonNull)
                    .toList();
        if (!failures.isEmpty()) {
            log.warn(
                    "Partial provider failures for {}->{}: {}",
                    criteria.origin(), criteria.destination(), failures
            );
        }
        log.debug(
                "Search for {}->{} produced {} deduplicated itineraries",
                criteria.origin(), criteria.destination(), itineraries.size()
        );
        return new FlightSearchResult(itineraries, failures);
    }

    private CompletableFuture<ProviderResult> request(
            FlightSearchProvider provider,
            FlightSearchCriteria criteria
    ) {
        return CompletableFuture
                .supplyAsync(() -> provider.search(criteria), executor)
                .orTimeout(providerTimeout.toMillis(), TimeUnit.MILLISECONDS)
                .handle((itineraries, error) -> {
                    if (error == null) {
                        return new ProviderResult(itineraries, null);
                    }
                    return new ProviderResult(
                            List.of(),
                            new ProviderSearchFailure(provider.provider(), failureReason(error))
                    );
                });
    }

    private static String failureReason(Throwable error) {
        Throwable cause = error instanceof CompletionException && error.getCause() != null
                ? error.getCause()
                : error;
        if (cause instanceof TimeoutException) {
            log.debug("Provider timed out", cause);
            return "Provider response timed out after the configured limit";
        }
        if (cause instanceof ProviderFailureException) {
            log.debug("Provider request failed: {}", cause.getMessage());
            return cause.getMessage();
        }
        log.debug("Provider request failed unexpectedly", cause);
        return "Provider request failed: " + cause.getClass().getSimpleName();
    }

    private static FlightItinerary cheapest(FlightItinerary first, FlightItinerary second) {
        return second.supplierPrice().isLessThan(first.supplierPrice()) ? second : first;
    }

    private static boolean matches(FlightSearchCriteria criteria, FlightOffer flight) {
        boolean isWithinMaximumPrice = criteria.maxPrice() == null
                || flight.sellingPrice().amount().compareTo(criteria.maxPrice()) <= 0;
        boolean matchesCarrier = criteria.carrier() == null
                || flight.itinerary().segments().stream()
                        .anyMatch(segment -> criteria.carrier().equals(segment.carrier().code()));
        return isWithinMaximumPrice && matchesCarrier;
    }

    private record ProviderResult(
            List<FlightItinerary> itineraries,
            ProviderSearchFailure failure
    ) {
        private ProviderResult {
            Objects.requireNonNull(itineraries, "Itineraries must not be null");
        }
    }
}
