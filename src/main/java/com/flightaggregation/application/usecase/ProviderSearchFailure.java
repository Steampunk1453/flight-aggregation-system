package com.flightaggregation.application.usecase;

import com.flightaggregation.domain.model.ProviderId;

import java.util.Objects;
import java.io.Serializable;

public record ProviderSearchFailure(ProviderId provider, String reason) implements Serializable {

    public ProviderSearchFailure {
        Objects.requireNonNull(provider, "Provider must not be null");
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Failure reason must not be blank");
        }
    }
}
