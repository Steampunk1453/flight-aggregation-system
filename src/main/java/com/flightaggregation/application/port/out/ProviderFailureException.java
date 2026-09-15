package com.flightaggregation.application.port.out;

public class ProviderFailureException extends RuntimeException {

    public ProviderFailureException(String message) {
        super(message);
    }
}
