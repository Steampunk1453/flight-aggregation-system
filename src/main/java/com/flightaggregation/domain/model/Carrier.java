package com.flightaggregation.domain.model;

import java.io.Serializable;

public record Carrier(String code, String name) implements Serializable {

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
