package com.flightaggregation.infrastructure.adapter.provider;

import com.flightaggregation.application.usecase.FlightSearchCriteria;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MockProvidersTest {

    private static final FlightSearchCriteria CRITERIA =
            new FlightSearchCriteria("MAD", "JFK", LocalDate.of(2026, 10, 1));

    @Test
    void parsesEachLegacyProviderFormatIntoTheDomainModel() {
        var providers = List.of(
                new MockAlphaFlightProvider(fastConfig()),
                new MockBetaFlightProvider(fastConfig()),
                new MockGammaFlightProvider(fastConfig())
        );

        providers.forEach(provider -> {
            var itineraries = provider.search(CRITERIA);
            assertEquals(1, itineraries.size());
            assertEquals("MAD", itineraries.getFirst().origin());
            assertEquals("JFK", itineraries.getFirst().destination());
            assertEquals("OA101", itineraries.getFirst().segments().getFirst().flightNumber());
        });
    }

    @Test
    void exposesControlledProviderFailures() {
        var provider = new MockAlphaFlightProvider(
                new SimulatedProviderConfig(Duration.ZERO, true)
        );

        assertThrows(ProviderRequestException.class, () -> provider.search(CRITERIA));
    }

    private static SimulatedProviderConfig fastConfig() {
        return new SimulatedProviderConfig(Duration.ZERO, false);
    }
}
