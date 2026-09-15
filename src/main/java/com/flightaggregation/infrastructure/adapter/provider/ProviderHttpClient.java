package com.flightaggregation.infrastructure.adapter.provider;

import com.flightaggregation.application.dto.FlightSearchCriteria;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

final class ProviderHttpClient {

    private static final Logger log = LoggerFactory.getLogger(ProviderHttpClient.class);

    private final HttpClient httpClient;

    ProviderHttpClient(HttpClient httpClient) {
        this.httpClient = Objects.requireNonNull(httpClient, "HTTP client must not be null");
    }

    String search(URI endpoint, FlightSearchCriteria criteria, String providerName) {
        Objects.requireNonNull(endpoint, "Endpoint must not be null");
        String separator = endpoint.getQuery() == null ? "?" : "&";
        URI requestUri = URI.create(endpoint + separator
                + "origin=" + encode(criteria.origin())
                + "&destination=" + encode(criteria.destination())
                + "&departureDate=" + encode(criteria.departureDate().toString()));
        log.debug("Calling {} provider at {}", providerName, requestUri);
        try {
            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(requestUri).GET().build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("{} responded with status {}", providerName, response.statusCode());
                throw new ProviderRequestException(
                        providerName + " request failed with status " + response.statusCode()
                );
            }
            return response.body();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("{} request was interrupted", providerName);
            throw new ProviderRequestException(providerName + " request was interrupted");
        } catch (IOException exception) {
            log.warn("{} request failed: {}", providerName, exception.getMessage());
            throw new ProviderRequestException(providerName + " request failed");
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
