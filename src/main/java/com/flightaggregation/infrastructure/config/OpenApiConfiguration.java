package com.flightaggregation.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

    @Bean
    public OpenAPI flightAggregationOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Flight Aggregation & Cache Optimization System")
                        .description(
                                "Aggregates flight itineraries from multiple GDS providers, "
                                        + "deduplicates them, applies the OTA markup, and exposes "
                                        + "a paginated search API backed by Redis and PostgreSQL."
                        )
                        .version("v1")
                        .contact(new Contact().name("Flight Aggregation Team")));
    }
}
