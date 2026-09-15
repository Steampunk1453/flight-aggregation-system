package com.flightaggregation.infrastructure.adapter.cache;

import com.flightaggregation.application.port.in.SearchFlights;
import com.flightaggregation.application.dto.FlightSearchCriteria;
import com.flightaggregation.application.dto.FlightSearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

public final class CachedSearchFlights implements SearchFlights {

    private static final Logger log = LoggerFactory.getLogger(CachedSearchFlights.class);

    private final SearchFlights delegate;
    private final RedisFlightSearchCache cache;

    public CachedSearchFlights(
            SearchFlights delegate,
            RedisFlightSearchCache cache
    ) {
        this.delegate = Objects.requireNonNull(delegate, "Delegate must not be null");
        this.cache = Objects.requireNonNull(cache, "Cache must not be null");
    }

    @Override
    public FlightSearchResult search(FlightSearchCriteria criteria) {
        Objects.requireNonNull(criteria, "Search criteria must not be null");
        var cachedResult = cache.find(criteria);
        if (cachedResult.isPresent()) {
            log.debug("Cache hit for {}->{} on {}", criteria.origin(), criteria.destination(), criteria.departureDate());
            return cachedResult.get();
        }
        log.debug("Cache miss for {}->{} on {}", criteria.origin(), criteria.destination(), criteria.departureDate());
        FlightSearchResult result = delegate.search(criteria);
        cache.put(criteria, result);
        return result;
    }
}
