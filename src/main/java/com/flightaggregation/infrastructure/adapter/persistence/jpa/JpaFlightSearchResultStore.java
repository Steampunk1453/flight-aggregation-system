package com.flightaggregation.infrastructure.adapter.persistence.jpa;

import com.flightaggregation.application.port.out.FlightSearchResultStore;
import com.flightaggregation.application.dto.FlightSearchCriteria;
import com.flightaggregation.application.dto.FlightSearchPage;
import com.flightaggregation.application.dto.FlightSearchPageRequest;
import com.flightaggregation.application.dto.FlightOffer;
import org.springframework.data.domain.PageRequest;

import java.time.ZoneOffset;
import java.math.BigDecimal;
import java.util.List;

public final class JpaFlightSearchResultStore implements FlightSearchResultStore {

    private final MaterializedFlightJpaRepository repository;

    public JpaFlightSearchResultStore(MaterializedFlightJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public void upsertAll(List<FlightOffer> flights) {
        repository.saveAll(flights.stream().map(MaterializedFlightEntity::from).toList());
    }

    @Override
    public FlightSearchPage findPage(
            FlightSearchCriteria criteria,
            FlightSearchPageRequest pageRequest
    ) {
        var cursor = pageRequest.cursor();
        var dayStart = criteria.departureDate().atStartOfDay().atOffset(ZoneOffset.UTC);
        BigDecimal maxPrice = criteria.maxPrice() == null ? BigDecimal.ZERO : criteria.maxPrice();
        String carrier = criteria.carrier() == null ? "" : criteria.carrier();
        var pageable = PageRequest.of(0, pageRequest.pageSize() + 1);
        var flights = cursor == null
                ? repository.findFirstPage(
                        criteria.origin(),
                        criteria.destination(),
                        dayStart,
                        dayStart.plusDays(1),
                        criteria.maxPrice() != null,
                        maxPrice,
                        criteria.carrier() != null,
                        carrier,
                        pageable
                )
                : repository.findPageAfterCursor(
                        criteria.origin(),
                        criteria.destination(),
                        dayStart,
                        dayStart.plusDays(1),
                        criteria.maxPrice() != null,
                        maxPrice,
                        criteria.carrier() != null,
                        carrier,
                        cursor.departureAt(),
                        cursor.sellingPrice(),
                        cursor.id(),
                        pageable
                );
        boolean hasNextPage = flights.size() > pageRequest.pageSize();
        var pageFlights = hasNextPage ? flights.subList(0, pageRequest.pageSize()) : flights;
        var nextCursor = hasNextPage
                ? cursorFor(pageFlights.getLast())
                : null;
        return new FlightSearchPage(
                pageFlights.stream().map(MaterializedFlightEntity::toDomain).toList(),
                nextCursor,
                List.of()
        );
    }

    private static FlightSearchPageRequest.FlightSearchCursor cursorFor(MaterializedFlightEntity flight) {
        return new FlightSearchPageRequest.FlightSearchCursor(
                flight.departureAt(),
                flight.sellingAmount(),
                flight.id()
        );
    }
}
