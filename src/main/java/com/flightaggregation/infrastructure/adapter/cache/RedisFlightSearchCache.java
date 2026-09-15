package com.flightaggregation.infrastructure.adapter.cache;

import com.flightaggregation.application.dto.FlightSearchCriteria;
import com.flightaggregation.application.dto.FlightSearchResult;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

public final class RedisFlightSearchCache {

    private static final String KEY_PREFIX = "flight-search:";

    private final RedisTemplate<String, FlightSearchResult> redisTemplate;
    private final Duration timeToLive;

    public RedisFlightSearchCache(
            RedisTemplate<String, FlightSearchResult> redisTemplate,
            Duration timeToLive
    ) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "Redis template must not be null");
        this.timeToLive = Objects.requireNonNull(timeToLive, "Time to live must not be null");
        if (timeToLive.isNegative() || timeToLive.isZero()) {
            throw new IllegalArgumentException("Time to live must be positive");
        }
    }

    public Optional<FlightSearchResult> find(FlightSearchCriteria criteria) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(key(criteria)));
    }

    public void put(FlightSearchCriteria criteria, FlightSearchResult result) {
        redisTemplate.opsForValue().set(key(criteria), result, timeToLive);
    }

    private static String key(FlightSearchCriteria criteria) {
        String maxPrice = criteria.maxPrice() == null
                ? "any"
                : criteria.maxPrice().stripTrailingZeros().toPlainString();
        String carrier = criteria.carrier() == null ? "any" : criteria.carrier();
        return KEY_PREFIX
                + criteria.origin()
                + ":"
                + criteria.destination()
                + ":"
                + criteria.departureDate()
                + ":"
                + maxPrice
                + ":"
                + carrier;
    }
}
