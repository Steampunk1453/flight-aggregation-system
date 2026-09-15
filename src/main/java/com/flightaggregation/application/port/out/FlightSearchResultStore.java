package com.flightaggregation.application.port.out;

import com.flightaggregation.application.dto.FlightSearchCriteria;
import com.flightaggregation.application.dto.FlightSearchPage;
import com.flightaggregation.application.dto.FlightSearchPageRequest;
import com.flightaggregation.application.dto.FlightOffer;

import java.util.List;

public interface FlightSearchResultStore {

    void upsertAll(List<FlightOffer> flights);

    FlightSearchPage findPage(FlightSearchCriteria criteria, FlightSearchPageRequest pageRequest);
}
