package com.flightaggregation.domain.model;

public record Carrier(String code, String name) {

    public Carrier {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Carrier code must not be blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Carrier name must not be blank");
        }
        code = code.trim().toUpperCase();
        name = name.trim();
    }
}
