package com.flightaggregation.infrastructure.web;

import com.flightaggregation.application.port.in.SearchFlightPage;
import com.flightaggregation.application.usecase.FlightSearchPage;
import com.flightaggregation.application.usecase.SearchFlight;
import com.flightaggregation.domain.model.Carrier;
import com.flightaggregation.domain.model.FlightItinerary;
import com.flightaggregation.domain.model.FlightSegment;
import com.flightaggregation.domain.model.Money;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FlightSearchController.class)
@Import(FlightSearchControllerTest.WebTestConfiguration.class)
class FlightSearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SearchFlightPage searchFlightPage;

    @Test
    void returnsTheDeduplicatedFlightWithSupplierAndSellingPrices() throws Exception {
        when(searchFlightPage.search(any(), any())).thenReturn(new FlightSearchPage(
                List.of(new SearchFlight(
                        new FlightItinerary(
                                List.of(new FlightSegment(
                                        new Carrier("OA", "Omega Air"),
                                        "OA101",
                                        "MAD",
                                        "JFK",
                                        OffsetDateTime.parse("2026-10-01T10:00:00Z"),
                                        OffsetDateTime.parse("2026-10-01T12:00:00Z")
                                )),
                                new Money(new BigDecimal("115.00"), "EUR"),
                                "BETA"
                        ),
                        new Money(new BigDecimal("120.75"), "EUR")
                )),
                null,
                List.of()
        ));

        mockMvc.perform(get("/api/flights/search")
                        .queryParam("origin", "mad")
                        .queryParam("destination", "jfk")
                        .queryParam("departureDate", "2026-10-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flights.length()").value(1))
                .andExpect(jsonPath("$.flights[0].provider").value("BETA"))
                .andExpect(jsonPath("$.flights[0].supplierPrice.amount").value(115.00))
                .andExpect(jsonPath("$.flights[0].sellingPrice.amount").value(120.75))
                .andExpect(jsonPath("$.providerFailures.length()").value(0))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
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

    @TestConfiguration
    static class WebTestConfiguration {

        @Bean
        SearchFlightPage searchFlightPage() {
            return mock(SearchFlightPage.class);
        }
    }
}
