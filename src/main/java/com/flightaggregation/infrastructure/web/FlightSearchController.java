package com.flightaggregation.infrastructure.web;

import com.flightaggregation.application.usecase.FlightSearchCriteria;
import com.flightaggregation.application.usecase.SearchFlightsUseCase;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/flights")
public class FlightSearchController {

    private final SearchFlightsUseCase searchFlightsUseCase;

    public FlightSearchController(SearchFlightsUseCase searchFlightsUseCase) {
        this.searchFlightsUseCase = searchFlightsUseCase;
    }

    @GetMapping("/search")
    public FlightSearchResponse search(
            @RequestParam String origin,
            @RequestParam String destination,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate departureDate
    ) {
        var criteria = new FlightSearchCriteria(origin, destination, departureDate);
        return FlightSearchResponse.from(searchFlightsUseCase.search(criteria));
    }
}
