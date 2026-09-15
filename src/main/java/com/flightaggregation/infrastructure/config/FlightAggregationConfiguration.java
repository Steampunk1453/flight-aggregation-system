package com.flightaggregation.infrastructure.config;

import com.flightaggregation.application.dto.FlightSearchResult;
import com.flightaggregation.application.usecase.SearchPagedFlightsUseCase;
import com.flightaggregation.application.usecase.SearchFlightsUseCase;
import com.flightaggregation.application.port.in.SearchPagedFlights;
import com.flightaggregation.application.port.in.SearchFlights;
import com.flightaggregation.application.port.out.FlightSearchProvider;
import com.flightaggregation.application.port.out.FlightSearchResultStore;
import com.flightaggregation.domain.policy.FixedMarkupPolicy;
import com.flightaggregation.domain.policy.MarkupPolicy;
import com.flightaggregation.domain.policy.MarkupRate;
import com.flightaggregation.infrastructure.adapter.cache.CachedSearchFlights;
import com.flightaggregation.infrastructure.adapter.provider.MockAlphaFlightProvider;
import com.flightaggregation.infrastructure.adapter.provider.MockBetaFlightProvider;
import com.flightaggregation.infrastructure.adapter.provider.MockGammaFlightProvider;
import com.flightaggregation.infrastructure.adapter.cache.RedisFlightSearchCache;
import com.flightaggregation.infrastructure.adapter.persistence.jpa.JpaFlightSearchResultStore;
import com.flightaggregation.infrastructure.adapter.persistence.jpa.MaterializedFlightJpaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class FlightAggregationConfiguration {

    @Bean(destroyMethod = "close")
    public ExecutorService flightProviderExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean
    public FlightSearchProvider mockAlphaFlightProvider(
            @Value("${flight.providers.alpha.url}") String endpoint
    ) {
        return new MockAlphaFlightProvider(URI.create(endpoint), HttpClient.newHttpClient());
    }

    @Bean
    public FlightSearchProvider mockBetaFlightProvider(
            @Value("${flight.providers.beta.url}") String endpoint
    ) {
        return new MockBetaFlightProvider(URI.create(endpoint), HttpClient.newHttpClient());
    }

    @Bean
    public FlightSearchProvider mockGammaFlightProvider(
            @Value("${flight.providers.gamma.url}") String endpoint
    ) {
        return new MockGammaFlightProvider(URI.create(endpoint), HttpClient.newHttpClient());
    }

    @Bean
    public SearchFlightsUseCase searchFlightsUseCase(
            List<FlightSearchProvider> flightProviders,
            ExecutorService flightProviderExecutor,
            MarkupPolicy markupPolicy,
            @Value("${flight.providers.timeout:PT3S}") Duration providerTimeout
    ) {
        return new SearchFlightsUseCase(
                flightProviders,
                providerTimeout,
                flightProviderExecutor,
                markupPolicy
        );
    }

    @Bean
    public MarkupPolicy markupPolicy(
            @Value("${flight.pricing.markup-percentage:5}") BigDecimal percentage
    ) {
        return new FixedMarkupPolicy(new MarkupRate(percentage));
    }

    @Bean
    public FlightSearchResultStore flightSearchResultStore(MaterializedFlightJpaRepository repository) {
        return new JpaFlightSearchResultStore(repository);
    }

    @Bean
    public SearchPagedFlights searchPagedFlights(
            SearchFlights searchFlights,
            FlightSearchResultStore resultStore
    ) {
        return new SearchPagedFlightsUseCase(searchFlights, resultStore);
    }

    @Bean
    @ConditionalOnProperty(
            name = "flight.search.cache.enabled",
            havingValue = "true"
    )
    public RedisTemplate<String, FlightSearchResult> flightSearchRedisTemplate(
            RedisConnectionFactory connectionFactory
    ) {
        var template = new RedisTemplate<String, FlightSearchResult>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new JdkSerializationRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    @ConditionalOnProperty(
            name = "flight.search.cache.enabled",
            havingValue = "true"
    )
    public RedisFlightSearchCache flightSearchCache(
            RedisTemplate<String, FlightSearchResult> flightSearchRedisTemplate,
            @Value("${flight.search.cache.ttl:PT5M}") Duration timeToLive
    ) {
        return new RedisFlightSearchCache(flightSearchRedisTemplate, timeToLive);
    }

    @Bean
    @Primary
    @ConditionalOnProperty(
            name = "flight.search.cache.enabled",
            havingValue = "true"
    )
    public SearchFlights cachedSearchFlights(
            SearchFlightsUseCase searchFlightsUseCase,
            RedisFlightSearchCache flightSearchCache
    ) {
        return new CachedSearchFlights(searchFlightsUseCase, flightSearchCache);
    }

    @Bean
    @Primary
    @ConditionalOnProperty(
            name = "flight.search.cache.enabled",
            havingValue = "false",
            matchIfMissing = true
    )
    public SearchFlights directSearchFlights(SearchFlightsUseCase searchFlightsUseCase) {
        return searchFlightsUseCase;
    }
}
