package com.flightaggregation.infrastructure.adapter.cache;

import com.flightaggregation.application.port.in.SearchFlights;
import com.flightaggregation.application.usecase.FlightSearchCriteria;
import com.flightaggregation.application.usecase.FlightSearchResult;
import com.flightaggregation.infrastructure.adapter.observability.FlightSearchCompletedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CachedSearchFlightsTest {

    private static final FlightSearchCriteria CRITERIA =
            new FlightSearchCriteria("MAD", "JFK", LocalDate.of(2026, 10, 1));
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    @Test
    void returnsACachedResultWithoutCallingTheProviderSearch() {
        FlightSearchResult cachedResult = new FlightSearchResult(java.util.List.of(), java.util.List.of());
        var cache = cacheReturning(cachedResult, cachedResult);
        var delegate = new CountingSearchFlights();
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        var searchFlights = new CachedSearchFlights(delegate, cache, eventPublisher);

        FlightSearchResult result = searchFlights.search(CRITERIA);

        assertSame(cachedResult, result);
        assertEquals(0, delegate.calls);
        verify(eventPublisher).publishEvent(any(FlightSearchCompletedEvent.class));
    }

    @Test
    void cachesTheResultReturnedByTheProviderSearchOnAMiss() {
        FlightSearchResult liveResult = new FlightSearchResult(java.util.List.of(), java.util.List.of());
        var cache = cacheReturning(null, liveResult);
        var delegate = new CountingSearchFlights(liveResult);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        var searchFlights = new CachedSearchFlights(delegate, cache, eventPublisher);

        FlightSearchResult firstResult = searchFlights.search(CRITERIA);
        FlightSearchResult secondResult = searchFlights.search(CRITERIA);

        assertSame(liveResult, firstResult);
        assertSame(liveResult, secondResult);
        assertEquals(1, delegate.calls);
        verify(eventPublisher, org.mockito.Mockito.times(2))
                .publishEvent(any(FlightSearchCompletedEvent.class));
    }

    private static final class CountingSearchFlights implements SearchFlights {

        private final FlightSearchResult result;
        private int calls;

        private CountingSearchFlights() {
            this(new FlightSearchResult(java.util.List.of(), java.util.List.of()));
        }

        private CountingSearchFlights(FlightSearchResult result) {
            this.result = result;
        }

        @Override
        public FlightSearchResult search(FlightSearchCriteria criteria) {
            calls++;
            return result;
        }
    }

    @SuppressWarnings("unchecked")
    private static RedisFlightSearchCache cacheReturning(
            FlightSearchResult firstResult,
            FlightSearchResult subsequentResult
    ) {
        RedisTemplate<String, FlightSearchResult> redisTemplate = mock(RedisTemplate.class);
        ValueOperations<String, FlightSearchResult> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(firstResult, subsequentResult);
        return new RedisFlightSearchCache(redisTemplate, CACHE_TTL);
    }
}
