package com.flightaggregation.infrastructure.adapter.provider;

import java.time.Duration;
import java.util.Objects;

public record SimulatedProviderConfig(Duration latency, boolean failRequests) {

    public SimulatedProviderConfig {
        Objects.requireNonNull(latency, "Latency must not be null");
        if (latency.isNegative()) {
            throw new IllegalArgumentException("Latency must not be negative");
        }
    }

    public static SimulatedProviderConfig defaults() {
        return new SimulatedProviderConfig(Duration.ofMillis(100), false);
    }
}
