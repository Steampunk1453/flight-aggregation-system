package com.flightaggregation.infrastructure.web;

import com.flightaggregation.application.port.in.SearchPagedFlights;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FlightSearchController.class)
class ApiErrorResponseTest {

    @MockitoBean
    private SearchPagedFlights searchPagedFlights;

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Returns a structured error when a required search parameter is missing")
    void returnsStructuredErrorForMissingRequiredParameter() throws Exception {
        mockMvc.perform(get("/api/flights/search")
                        .queryParam("destination", "JFK")
                        .queryParam("departureDate", "2026-10-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("MISSING_REQUIRED_PARAMETER"))
                .andExpect(jsonPath("$.message").value("Missing required parameter: origin"))
                .andExpect(jsonPath("$.path").value("/api/flights/search"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    @DisplayName("Returns a structured error when a request parameter cannot be converted to its expected type")
    void returnsStructuredErrorForInvalidParameterType() throws Exception {
        mockMvc.perform(get("/api/flights/search")
                        .queryParam("origin", "MAD")
                        .queryParam("destination", "JFK")
                        .queryParam("departureDate", "not-a-date"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER_TYPE"))
                .andExpect(jsonPath("$.message").value("Invalid value for parameter: departureDate"))
                .andExpect(jsonPath("$.path").value("/api/flights/search"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    @DisplayName("Returns a structured error when search criteria fail business validation")
    void returnsStructuredErrorForInvalidSearchCriteria() throws Exception {
        mockMvc.perform(get("/api/flights/search")
                        .queryParam("origin", "MA")
                        .queryParam("destination", "JFK")
                        .queryParam("departureDate", "2026-10-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("Origin must be a three-letter airport code"))
                .andExpect(jsonPath("$.path").value("/api/flights/search"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    @DisplayName("Returns a structured error when a pagination cursor cannot be decoded")
    void returnsStructuredErrorForMalformedCursor() throws Exception {
        mockMvc.perform(get("/api/flights/search")
                        .queryParam("origin", "MAD")
                        .queryParam("destination", "JFK")
                        .queryParam("departureDate", "2026-10-01")
                        .queryParam("cursor", "not-a-cursor"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("Invalid pagination cursor"))
                .andExpect(jsonPath("$.path").value("/api/flights/search"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    @DisplayName("Hides unexpected exception details behind a structured internal-error response")
    void returnsStructuredErrorForUnexpectedFailure() throws Exception {
        given(searchPagedFlights.search(any(), any())).willThrow(new IllegalStateException("Database unavailable"));

        mockMvc.perform(get("/api/flights/search")
                        .queryParam("origin", "MAD")
                        .queryParam("destination", "JFK")
                        .queryParam("departureDate", "2026-10-01"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
                .andExpect(jsonPath("$.path").value("/api/flights/search"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }
}
