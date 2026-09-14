package com.flightaggregation.infrastructure.adapter.persistence.jpa;

import com.flightaggregation.application.usecase.SearchFlight;
import com.flightaggregation.domain.model.FlightItinerary;
import com.flightaggregation.domain.model.Money;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "materialized_flights",
        uniqueConstraints = @UniqueConstraint(name = "uk_materialized_flight_key", columnNames = "deduplicationKey"),
        indexes = @Index(
                name = "idx_materialized_flights_search_keyset",
                columnList = "origin, destination, departureAt, carrierCode, sellingAmount, id"
        )
)
public class MaterializedFlightEntity {

    @Id
    @Column(nullable = false, updatable = false, length = 36)
    private String id;

    @Column(nullable = false, updatable = false, length = 1024)
    private String deduplicationKey;

    @Column(nullable = false, length = 3)
    private String origin;

    @Column(nullable = false, length = 3)
    private String destination;

    @Column(nullable = false)
    private OffsetDateTime departureAt;

    @Column(nullable = false, length = 3)
    private String carrierCode;

    @Column(nullable = false, length = 32)
    private String provider;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal supplierAmount;

    @Column(nullable = false, length = 3)
    private String supplierCurrency;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal sellingAmount;

    @Column(nullable = false, length = 3)
    private String sellingCurrency;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "materialized_flight_segments", joinColumns = @JoinColumn(name = "flight_id"))
    @OrderColumn(name = "segment_position")
    private List<MaterializedFlightSegment> segments;

    protected MaterializedFlightEntity() {
    }

    private MaterializedFlightEntity(SearchFlight flight) {
        FlightItinerary itinerary = flight.itinerary();
        deduplicationKey = itinerary.deduplicationKey();
        id = UUID.nameUUIDFromBytes(deduplicationKey.getBytes(StandardCharsets.UTF_8)).toString();
        origin = itinerary.origin();
        destination = itinerary.destination();
        departureAt = itinerary.departureAt();
        carrierCode = itinerary.segments().getFirst().carrier().code();
        provider = itinerary.provider();
        supplierAmount = itinerary.supplierPrice().amount();
        supplierCurrency = itinerary.supplierPrice().currency();
        sellingAmount = flight.sellingPrice().amount();
        sellingCurrency = flight.sellingPrice().currency();
        segments = itinerary.segments().stream().map(MaterializedFlightSegment::from).toList();
    }

    static MaterializedFlightEntity from(SearchFlight flight) {
        return new MaterializedFlightEntity(flight);
    }

    String id() {
        return id;
    }

    OffsetDateTime departureAt() {
        return departureAt;
    }

    BigDecimal sellingAmount() {
        return sellingAmount;
    }

    SearchFlight toDomain() {
        return new SearchFlight(
                new FlightItinerary(
                        segments.stream().map(MaterializedFlightSegment::toDomain).toList(),
                        new Money(supplierAmount, supplierCurrency),
                        provider
                ),
                new Money(sellingAmount, sellingCurrency)
        );
    }
}
