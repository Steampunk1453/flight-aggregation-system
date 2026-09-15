package com.flightaggregation.infrastructure.web;

import com.flightaggregation.application.port.in.SearchPagedFlights;
import com.flightaggregation.application.dto.FlightSearchCriteria;
import com.flightaggregation.application.dto.FlightSearchPageRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.math.BigDecimal;

@RestController
@RequestMapping("/api/flights")
@Tag(name = "Flight search", description = "Aggregated, deduplicated, and paginated flight search")
public class FlightSearchController {

    private static final Logger log = LoggerFactory.getLogger(FlightSearchController.class);

    private final SearchPagedFlights searchPagedFlights;

    public FlightSearchController(SearchPagedFlights searchPagedFlights) {
        this.searchPagedFlights = searchPagedFlights;
    }

    @Operation(
            summary = "Search flights",
            description = "Queries all GDS providers, deduplicates itineraries, applies the OTA markup, "
                    + "materializes results in PostgreSQL, and returns a keyset-paginated page."
    )
    @GetMapping("/search")
    public FlightSearchResponse search(
            @Parameter(description = "Origin airport IATA code", example = "MAD")
            @RequestParam String origin,
            @Parameter(description = "Destination airport IATA code", example = "JFK")
            @RequestParam String destination,
            @Parameter(description = "Departure date (ISO-8601)", example = "2026-10-01")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate departureDate,
            @Parameter(description = "Maximum selling price filter")
            @RequestParam(required = false) BigDecimal maxPrice,
            @Parameter(description = "Carrier IATA code filter", example = "OA")
            @RequestParam(required = false) String carrier,
            @Parameter(description = "Page size, defaults to 20")
            @RequestParam(required = false) Integer pageSize,
            @Parameter(description = "Opaque keyset cursor returned by a previous page")
            @RequestParam(required = false) String cursor
    ) {
        log.info(
                "Searching flights origin={} destination={} departureDate={} maxPrice={} carrier={} pageSize={} hasCursor={}",
                origin, destination, departureDate, maxPrice, carrier, pageSize, cursor != null
        );
        var criteria = new FlightSearchCriteria(origin, destination, departureDate, maxPrice, carrier);
        var pageRequest = new FlightSearchPageRequest(
                pageSize == null ? FlightSearchPageRequest.DEFAULT_PAGE_SIZE : pageSize,
                FlightSearchCursorCodec.decode(cursor)
        );
        var page = searchPagedFlights.search(criteria, pageRequest);
        log.info(
                "Search completed origin={} destination={} flights={} failedProviders={} hasNextPage={}",
                origin, destination, page.flights().size(), page.providerFailures().size(),
                page.nextCursor() != null
        );
        return FlightSearchResponse.from(page);
    }
}
