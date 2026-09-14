package com.flightaggregation.infrastructure.adapter.persistence.jpa;

import com.flightaggregation.domain.model.Carrier;
import com.flightaggregation.domain.model.FlightSegment;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.time.OffsetDateTime;

@Embeddable
public class MaterializedFlightSegment {

    @Column(nullable = false, length = 3)
    private String carrierCode;

    @Column(nullable = false)
    private String carrierName;

    @Column(nullable = false)
    private String flightNumber;

    @Column(nullable = false, length = 3)
    private String origin;

    @Column(nullable = false, length = 3)
    private String destination;

    @Column(nullable = false)
    private OffsetDateTime departureAt;

    @Column(nullable = false)
    private OffsetDateTime arrivalAt;

    protected MaterializedFlightSegment() {
    }

    private MaterializedFlightSegment(FlightSegment segment) {
        carrierCode = segment.carrier().code();
        carrierName = segment.carrier().name();
        flightNumber = segment.flightNumber();
        origin = segment.origin();
        destination = segment.destination();
        departureAt = segment.departureAt();
        arrivalAt = segment.arrivalAt();
    }

    static MaterializedFlightSegment from(FlightSegment segment) {
        return new MaterializedFlightSegment(segment);
    }

    FlightSegment toDomain() {
        return new FlightSegment(
                new Carrier(carrierCode, carrierName),
                flightNumber,
                origin,
                destination,
                departureAt,
                arrivalAt
        );
    }
}
