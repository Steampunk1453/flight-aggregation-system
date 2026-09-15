package com.flightaggregation.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CarrierTest {

    @Test
    @DisplayName("When creating a carrier, normalizes the code to uppercase and trims spaces from the name")
    void normalizesCodeAndTrimsName() {
        Carrier carrier = new Carrier(" ab ", "  Mock Air  ");

        assertEquals("AB", carrier.code());
        assertEquals("Mock Air", carrier.name());
    }

    @Test
    @DisplayName("Rejects creating a carrier when the code is blank")
    void rejectsBlankCode() {
        assertThrows(IllegalArgumentException.class, () -> new Carrier(" ", "Mock Air"));
    }

    @Test
    @DisplayName("Rejects creating a carrier when the code is null")
    void rejectsNullCode() {
        assertThrows(IllegalArgumentException.class, () -> new Carrier(null, "Mock Air"));
    }

    @Test
    @DisplayName("Rejects creating a carrier when the name is blank")
    void rejectsBlankName() {
        assertThrows(IllegalArgumentException.class, () -> new Carrier("AB", " "));
    }

    @Test
    @DisplayName("Rejects creating a carrier when the name is null")
    void rejectsNullName() {
        assertThrows(IllegalArgumentException.class, () -> new Carrier("AB", null));
    }
}
