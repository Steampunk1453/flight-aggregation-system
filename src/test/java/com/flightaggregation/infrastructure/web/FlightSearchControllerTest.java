package com.flightaggregation.infrastructure.web;

import com.flightaggregation.infrastructure.config.FlightAggregationConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FlightSearchController.class)
@Import(FlightAggregationConfiguration.class)
@TestPropertySource(properties = "flight.search.cache.enabled=false")
class FlightSearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsTheDeduplicatedFlightWithSupplierAndSellingPrices() throws Exception {
        mockMvc.perform(get("/api/flights/search")
                        .queryParam("origin", "mad")
                        .queryParam("destination", "jfk")
                        .queryParam("departureDate", "2026-10-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flights.length()").value(1))
                .andExpect(jsonPath("$.flights[0].provider").value("BETA"))
                .andExpect(jsonPath("$.flights[0].supplierPrice.amount").value(115.00))
                .andExpect(jsonPath("$.flights[0].sellingPrice.amount").value(120.75))
                .andExpect(jsonPath("$.providerFailures.length()").value(0));
    }

    @Test
    void rejectsAnInvalidSearchRequest() throws Exception {
        mockMvc.perform(get("/api/flights/search")
                        .queryParam("origin", "MAD")
                        .queryParam("destination", "MAD")
                        .queryParam("departureDate", "2026-10-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Origin and destination must differ"));
    }
}
