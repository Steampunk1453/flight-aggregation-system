package com.flightaggregation.infrastructure.adapter.provider;

import com.flightaggregation.domain.repository.ProviderFailureException;

public final class ProviderRequestException extends ProviderFailureException {

    public ProviderRequestException(String message) {
        super(message);
    }
}
