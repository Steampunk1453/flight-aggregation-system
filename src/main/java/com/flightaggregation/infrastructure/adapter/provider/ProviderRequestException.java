package com.flightaggregation.infrastructure.adapter.provider;

import com.flightaggregation.application.port.out.ProviderFailureException;

public final class ProviderRequestException extends ProviderFailureException {

    public ProviderRequestException(String message) {
        super(message);
    }
}
