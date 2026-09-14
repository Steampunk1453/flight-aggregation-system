package com.flightaggregation.infrastructure.config;

import com.flightaggregation.application.usecase.SearchFlightsUseCase;
import com.flightaggregation.domain.repository.FlightProviderRepository;
import com.flightaggregation.infrastructure.adapter.provider.MockAlphaFlightProvider;
import com.flightaggregation.infrastructure.adapter.provider.MockBetaFlightProvider;
import com.flightaggregation.infrastructure.adapter.provider.MockGammaFlightProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
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
    public FlightProviderRepository mockAlphaFlightProvider() {
        return new MockAlphaFlightProvider();
    }

    @Bean
    public FlightProviderRepository mockBetaFlightProvider() {
        return new MockBetaFlightProvider();
    }

    @Bean
    public FlightProviderRepository mockGammaFlightProvider() {
        return new MockGammaFlightProvider();
    }

    @Bean
    public SearchFlightsUseCase searchFlightsUseCase(
            List<FlightProviderRepository> flightProviders,
            ExecutorService flightProviderExecutor
    ) {
        return new SearchFlightsUseCase(
                flightProviders,
                Duration.ofSeconds(3),
                flightProviderExecutor
        );
    }
}
