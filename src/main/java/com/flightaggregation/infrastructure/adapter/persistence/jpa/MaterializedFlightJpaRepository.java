package com.flightaggregation.infrastructure.adapter.persistence.jpa;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public interface MaterializedFlightJpaRepository extends JpaRepository<MaterializedFlightEntity, String> {

    @Query("""
            select flight from MaterializedFlightEntity flight
            where flight.origin = :origin
              and flight.destination = :destination
              and flight.departureAt >= :dayStart
              and flight.departureAt < :nextDayStart
              and (:filterByMaxPrice = false or flight.sellingAmount <= :maxPrice)
              and (
                  :filterByCarrier = false
                  or exists (
                      select 1 from MaterializedFlightEntity matchingFlight
                      join matchingFlight.segments segment
                      where matchingFlight = flight and segment.carrierCode = :carrier
                  )
              )
            order by flight.departureAt asc, flight.sellingAmount asc, flight.id asc
            """)
    List<MaterializedFlightEntity> findFirstPage(
            @Param("origin") String origin,
            @Param("destination") String destination,
            @Param("dayStart") OffsetDateTime dayStart,
            @Param("nextDayStart") OffsetDateTime nextDayStart,
            @Param("filterByMaxPrice") boolean filterByMaxPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            @Param("filterByCarrier") boolean filterByCarrier,
            @Param("carrier") String carrier,
            Pageable pageable
    );

    @Query("""
            select flight from MaterializedFlightEntity flight
            where flight.origin = :origin
              and flight.destination = :destination
              and flight.departureAt >= :dayStart
              and flight.departureAt < :nextDayStart
              and (:filterByMaxPrice = false or flight.sellingAmount <= :maxPrice)
              and (
                  :filterByCarrier = false
                  or exists (
                      select 1 from MaterializedFlightEntity matchingFlight
                      join matchingFlight.segments segment
                      where matchingFlight = flight and segment.carrierCode = :carrier
                  )
              )
              and (
                  flight.departureAt > :cursorDeparture
                  or (flight.departureAt = :cursorDeparture and flight.sellingAmount > :cursorPrice)
                  or (
                      flight.departureAt = :cursorDeparture
                      and flight.sellingAmount = :cursorPrice
                      and flight.id > :cursorId
                  )
              )
            order by flight.departureAt asc, flight.sellingAmount asc, flight.id asc
            """)
    List<MaterializedFlightEntity> findPageAfterCursor(
            @Param("origin") String origin,
            @Param("destination") String destination,
            @Param("dayStart") OffsetDateTime dayStart,
            @Param("nextDayStart") OffsetDateTime nextDayStart,
            @Param("filterByMaxPrice") boolean filterByMaxPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            @Param("filterByCarrier") boolean filterByCarrier,
            @Param("carrier") String carrier,
            @Param("cursorDeparture") OffsetDateTime cursorDeparture,
            @Param("cursorPrice") BigDecimal cursorPrice,
            @Param("cursorId") String cursorId,
            Pageable pageable
    );
}
