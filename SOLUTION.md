# Flight Aggregation & Cache Optimization System

## Current status

The project currently implements the first version of the flight search flow:

```text
HTTP/frontend (filters + opaque cursor)
    -> paged search use case
    -> cached/live provider search in parallel
    -> normalization to FlightItinerary
    -> deduplication and lowest-price selection
    -> configured OTA markup policy
    -> PostgreSQL materialized search results
    -> keyset page
```

The simulated adapters represent three GDS providers with different legacy formats:

- **Mock-Alpha:** JSON list, with either flat direct-flight objects or an itinerary
  `segments` array.
- **Mock-Beta:** nested XML, with either a direct `<flight>` or nested `<segments>`.
- **Mock-Gamma:** comma-separated string stream (one or more newline-separated CSV
  records per response). A tenth optional itinerary ID groups multiple segment records
  into a connecting itinerary; the original nine-column direct-flight format remains
  supported.

Each provider is exposed as a WireMock HTTP service in Docker Compose. The application
calls the services concurrently through `FlightSearchProvider`; it does not construct
provider responses in process. This exercises the same network boundary, response
normalization, latency, timeout, and non-2xx handling expected of a real GDS adapter.

## Architectural decisions

### Clean Architecture

The domain does not depend on Spring, HTTP, parsers, or network clients:

```text
domain/
└── model/

application/
├── port/
│   ├── in/
│   └── out/
└── usecase/

infrastructure/
├── adapter/
└── config/
```

`FlightSearchProvider` is the contract required by the application to query providers.
Concrete implementations live under `infrastructure/adapter/provider`.

JSON, XML, and CSV/string formats exist only inside the adapters. Every response is converted to the shared `FlightItinerary` model before reaching the use case.

### DDD model

The application is organized around the **Flight Search** bounded context. Its ubiquitous
language is expressed by `FlightItinerary`, `FlightSegment`, `Carrier`, `Money`, and
`Provider`. These immutable types are value objects: a search result has no lifecycle
or transactional state to justify an entity or aggregate.

Commercial price calculation is a domain policy. `MarkupPolicy` calculates the selling
price and `MarkupRate` is the validated value object that represents its percentage.
`FixedMarkupPolicy` is the configured policy for this version; provider-, route-, date-,
or campaign-specific policies can implement the same domain contract without changing
the itinerary model or search use case.

### Live Search

The current implementation uses a **Live Search** strategy:

1. The user starts a search.
2. The system queries all three providers in parallel.
3. Available results are aggregated and deduplicated.
4. The response is returned to the user.

This decision prioritizes up-to-date prices. The aggregated response is also materialized
in PostgreSQL, giving the search API an indexed, queryable read model without putting
JPA concerns in the domain or live aggregation use case. The trade-off is that cache
misses still depend on the slowest provider until the configured timeout is reached.

### Concurrency and executor lifecycle

The use case receives an `ExecutorService` through its constructor. Spring configuration creates a shared virtual-thread executor:

```java
@Bean(destroyMethod = "close")
public ExecutorService flightProviderExecutor() {
    return Executors.newVirtualThreadPerTaskExecutor();
}
```

An executor is not created or destroyed for every request. Spring manages its lifecycle and closes it when the application shuts down.

Tests can provide their own executor, keeping the use case isolated and easy to test.

### Resilience

Each provider is queried independently and has a configurable timeout, with a default value of three seconds.

A provider error or timeout does not discard healthy responses. The result includes:

```java
List<FlightOffer> itineraries
List<ProviderSearchFailure> failures
```

This allows the system to display partial results and record which providers did not respond.

The three-second timeout is intentionally configured independently from the providers' documented maximum latency of five seconds. Waiting for the full five seconds would unnecessarily increase the latency of searches when a provider is slow. Instead, a slow provider is isolated after the configured timeout, while healthy providers can still contribute partial results.

The timeout is configurable, allowing it to be adjusted according to production latency requirements and observed provider performance.

### Deduplication and price selection

`FlightItinerary.deduplicationKey()` identifies an itinerary using:

- Airline code.
- Flight number.
- Origin and destination of each segment.
- Departure time normalized to UTC.
- Segment order.

When multiple providers offer the same combination, the itinerary with the lowest `supplierPrice` is retained.

For this system, two itineraries are considered identical when they have the same ordered segments and each segment has the same:

- Airline code.
- Flight number.
- Origin airport.
- Destination airport.
- Departure instant, normalized to UTC.

The provider and price are deliberately excluded from the deduplication key. They describe the offer source and commercial value, not the identity of the flight itinerary. Arrival time is also excluded because the requirement defines identity around the flight numbers, connections, and departure times. If the business later needs to distinguish schedule variants with the same departure, arrival time can be added to the key without changing the aggregation contract.

Segment order is part of the key, so a direct flight and a connecting itinerary cannot collide. Invalid connections and dead legs are rejected by the domain model before an itinerary can
participate in deduplication. Provider adapters discard an invalid itinerary record while
retaining other valid records in the same response, so one malformed legacy record does
not discard a provider's healthy offers.

### Supplier price and selling price

The original supplier price is not modified. The application result uses `FlightOffer`:

```java
public record FlightOffer(
        FlightItinerary itinerary,
        Money sellingPrice
) {
}
```

This preserves both values:

```text
supplierPrice = price received from the GDS
sellingPrice  = supplierPrice * 1.05
```

Keeping the values separate preserves the original price for auditing and allows the frontend to display the final selling price explicitly.

## HTTP mock GDS providers

The mock GDSs are real HTTP APIs rather than in-process response builders. This is
intentional: the exercise specifies APIs with legacy protocols, latency, and failures,
which are integration concerns that should remain outside the application core.

Compose starts one WireMock service per provider:

- **Mock-Alpha** returns JSON after 100 ms.
- **Mock-Beta** returns nested XML after 500 ms.
- **Mock-Gamma** returns CSV after 1,000 ms.

The controlled `origin=ERR` scenario makes Alpha return HTTP 500. The controlled
`origin=SLO` scenario makes Gamma wait five seconds; the application's three-second
provider timeout returns the Alpha/Beta partial result instead. These scenarios make
failure and timeout isolation reproducible without random test behavior. They are
verified both by `ProviderResiliencyTest` (a self-contained HTTP-server-based test) and
manually against the live Compose stack: querying `origin=ERR`/`origin=SLO` returns a
`200 OK` with the healthy providers' flights and a populated `providerFailures` array
instead of failing the whole request.

## Current test coverage

The test suite covers:

- Domain model validation.
- 5% markup calculation.
- Deduplication key generation.
- Connection validation.
- JSON, XML, and multi-line CSV/string-stream parsing, including connecting itineraries.
- Granular rejection of dead legs and invalid connections without dropping valid records
  from the same provider response.
- Controlled provider failures.
- Cheapest-itinerary selection.
- Partial-failure tolerance, including an end-to-end test
  (`ProviderResiliencyTest`) that proves the use case still returns results when one
  real HTTP provider answers with `500` and another exceeds the configured timeout.
- Per-provider timeouts.
- Supplier price versus selling price.
- `maxPrice` filtering on the selling price and carrier filtering.
- Combined price/carrier filtering and the all-provider-failure response.
- MVC query-parameter and cursor handling.
- Redis caching and PostgreSQL keyset pagination against real containers through
  Testcontainers.
- End-to-end Spring MVC searches through HTTP providers, Redis, PostgreSQL,
  deduplication, filters, partial failures, and cursor pagination.
- A separately runnable 100,000–1,000,000-row PostgreSQL keyset-volume profile.

## REST API

The live-search use case is exposed through Spring MVC:

```http
GET /api/flights/search?origin=MAD&destination=JFK&departureDate=2026-10-01&maxPrice=200&carrier=OA&pageSize=20
```

`maxPrice` is applied to the OTA selling price and `carrier` matches a carrier in the
itinerary. `pageSize` defaults to 20 and is bounded by the application. Responses
contain an opaque `nextCursor`; pass it unchanged as `cursor` to read the next page.
The endpoint returns deduplicated flights with their supplier and selling prices, their
segments, and any provider failures. Invalid search criteria or malformed cursors
produce a `400 Bad Request` response with an explanatory message.

A centralized `@RestControllerAdvice` (`ApiExceptionHandler`) maps validation errors
(`IllegalArgumentException`), missing/invalid query parameters
(`MissingServletRequestParameterException`, `MethodArgumentTypeMismatchException`) to
`400 Bad Request`, and any unanticipated exception to a generic `500 Internal Server
Error` without leaking internal details. All handled exceptions are logged.

### API documentation (OpenAPI/Swagger)

`springdoc-openapi` generates the OpenAPI document from the controller and its
parameters. Interactive documentation is available at:

```text
/swagger-ui.html
/v3/api-docs
```

`OpenApiConfiguration` supplies the API title, description, and version metadata.

### Observability and logging

Key request/response points use SLF4J:

- `FlightSearchController` logs incoming search parameters and the outcome (result
  count, failed providers, whether another page exists).
- `SearchFlightsUseCase` logs partial provider failures at `WARN` and search volume at
  `DEBUG`.
- `ProviderHttpClient` logs each outbound provider call and any non-2xx/IO/timeout
  failure.
- `CachedSearchFlights` logs cache hits/misses.
- `ApiExceptionHandler` logs rejected requests at `WARN` and unexpected failures at
  `ERROR` with the full stack trace.

Log level and pattern are configured in `application.yml` under `logging`.

### Health and metrics (Spring Boot Actuator)

Actuator is enabled with `health`, `info`, and `metrics` exposed over HTTP:

```text
GET /actuator/health
GET /actuator/info
GET /actuator/metrics
```

Health aggregates PostgreSQL and Redis connectivity, so `docker compose` and local
orchestration can use it as a real readiness/liveness signal for the `app` service.

`FlightAggregationConfiguration` owns the Spring beans for the three simulated providers,
the shared virtual-thread executor, and `SearchFlightsUseCase`. The use case has a
three-second provider timeout in the application configuration.

## Web interface

The static page at `/` supports origin, destination, departure date, maximum selling
price, and carrier filters. It renders the returned itineraries and exposes a
**Next page** button only while the API returns a cursor. Subsequent pages append to the
existing results, preserving the stable API order.

## Redis cache

Live-search results are cached in Redis for five minutes. The cache key is:

```text
flight-search:{origin}:{destination}:{departureDate}
```

The key also includes normalized `maxPrice` and `carrier` values, preventing filtered
and unfiltered searches from sharing a cached response. A cache hit returns the prior
aggregated result without querying providers; a cache miss performs the live search and
writes its result to Redis. This is configured through `flight.search.cache.enabled` and
`flight.search.cache.ttl`. The cache is enabled by default and expects Redis at Spring
Boot's standard local connection settings. Cached search values use Spring Data Redis'
JDK serializer, avoiding a runtime coupling to a particular Jackson major version.

## PostgreSQL configuration

Database connectivity is configured through
`FLIGHT_DATABASE_URL`, `FLIGHT_DATABASE_USERNAME`, and `FLIGHT_DATABASE_PASSWORD`; the
defaults expect a local `flight_aggregation` database. An earlier iteration also recorded
a search-audit trail (`flight_search_audits`) via a domain event
(`FlightSearchCompletedEvent`). It was removed once the materialized read model below
existed and Actuator/logging covered observability: the audit table duplicated
information already available from materialized rows plus structured logs, and it was
not an explicit assessment requirement, so it was dropped to keep the system simple
(KISS).

## PostgreSQL materialized search results and pagination

`materialized_flights` is the queryable read model for an aggregated search. It stores
the route, departure time, first carrier, provider, supplier and selling prices,
deduplication key, and normalized segments. Rows are upserted using a deterministic
identifier derived from the itinerary key, so repeated searches refresh the same offer
instead of duplicating it.

The endpoint uses **keyset pagination**, ordered by:

1. Departure time ascending.
2. Selling price ascending.
3. Deterministic materialized-flight ID ascending.

The cursor carries this three-part boundary, encoded at the web boundary. Keyset
pagination avoids the progressively expensive scans associated with large SQL offsets
and prevents duplicate/skipped results when new rows are materialized between requests.
The read model uses `(origin, destination, departureAt, sellingAmount, id)`, matching
the route/day filter and the keyset sort order. Carrier filtering is implemented as an
`EXISTS` condition over persisted segments, so it matches any leg of a connecting
itinerary; `materialized_flight_segments` has a `(carrierCode, flight_id)` index to
support that lookup. This avoids the incorrect shortcut of indexing only the first
carrier in an itinerary. Production workloads should evaluate additional partial or
specialized indexes from observed filter selectivity.

## Docker development environment

Docker Compose defines the complete local environment:

```text
app        -> http://localhost:8080
PostgreSQL -> localhost:5432
Redis      -> localhost:6379
Mock Alpha -> localhost:8081
Mock Beta  -> localhost:8082
Mock Gamma -> localhost:8083
```

Start Docker Desktop and run:

```bash
docker compose up --build
```

The development-only PostgreSQL credentials are `flight_aggregation` for both username
and password. The application container uses Docker service names rather than `localhost` and waits
for PostgreSQL, Redis, and all three mock GDS health checks before starting.

## Ports

The flight search use case owns its incoming and outgoing ports:

```text
application.port.in
└── SearchFlights
└── SearchPagedFlights

application.port.out
└── FlightSearchProvider
└── FlightSearchResultStore
```

The web controller calls the incoming port and provider adapters implement the outgoing
port. Domain models remain free of provider, cache, persistence, and web concerns.

Caching is a technical decorator in `infrastructure.adapter.cache`. It wraps
`SearchFlights` without becoming a dependency of the use case.

JPA-specific types (`MaterializedFlightEntity`, `MaterializedFlightJpaRepository`, and
`JpaFlightSearchResultStore`) are further isolated in
`infrastructure.adapter.persistence.jpa`.

## How to test the project

### Prerequisites

- **Java 25:** The Maven build targets Java 25 and uses Virtual Threads
  (`Executors.newVirtualThreadPerTaskExecutor()`) and Sequenced Collections
  (`List.getFirst()`). Make sure `JAVA_HOME` points to JDK 25.
- **Docker & Docker Compose:** Required for running the containers (PostgreSQL, Redis, WireMock providers) and Testcontainers-based integration tests.

### Running unit tests

Run the standard Maven test suite:

```bash
./mvnw test
# or with installed Maven:
mvn test
```

To run a specific test:

```bash
mvn test -Dtest=ProviderResiliencyTest
```

### Running integration tests

Integration tests (such as `PersistenceIntegrationTest`) use Testcontainers to spin up PostgreSQL containers automatically:

```bash
mvn test -Dtest=*IntegrationTest
```

### Manual testing with Docker Compose

1. Start all infrastructure and mock services:
   ```bash
   docker compose up --build
   ```
2. Access the OpenAPI / Swagger UI documentation and interactive API explorer:
   - Swagger UI: [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)
   - OpenAPI Spec: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)
3. Test search queries via cURL or browser:
   ```bash
   curl "http://localhost:8080/api/flights/search?origin=MAD&destination=JFK&departureDate=2026-10-01"
   ```
4. Verify individual WireMock provider endpoints:
   - Mock Alpha (JSON): `http://localhost:8081/__admin`
   - Mock Beta (XML): `http://localhost:8082/__admin`
   - Mock Gamma (CSV): `http://localhost:8083/__admin`

## Step-by-step guide (use cases)

This section walks through the main use cases end to end, using the
Docker Compose environment described above. Run `docker compose up --build` first and
wait for the `app` service to report healthy (`GET http://localhost:8080/actuator/health`).

Each use case below is explained both at the API level (`curl`) and at the **UX level**,
i.e. what to click and see in the web interface at `http://localhost:8080/`. The page
has a **Search flights** form (`Origin`, `Destination`, `Departure date`, `Maximum
price`, `Carrier`, `Page size` fields and a **Search flights** button), a results
section with a flight-card list, and a **Next page** button.

### 1. Happy path: search flights across all providers

**API:**
```bash
curl "http://localhost:8080/api/flights/search?origin=MAD&destination=JFK&departureDate=2026-10-01"
```

Expected: `200 OK` with a JSON body containing deduplicated `flights` (each with
`itinerary`, `supplierPrice`, and `sellingPrice`), an empty/absent `providerFailures`
array, and a `nextCursor` if more results exist than `pageSize`. This exercises Mock-Alpha
(JSON), Mock-Beta (XML), and Mock-Gamma (CSV) concurrently, normalization, deduplication,
lowest-price selection, and the 5% markup policy.

**UX:** Open `http://localhost:8080/`, type `MAD` in **Origin**, `JFK` in
**Destination**, pick `2026-10-01` in **Departure date**, leave **Maximum price** and
**Carrier** empty, and click **Search flights**. While the request is in flight the
button is disabled and reads "Searching...". Expected: the button re-enables, an
**Available flights** section appears with a result counter, and one card per itinerary
is rendered, each showing the route, every segment (carrier, flight number, departure
and arrival time), the selling price, and the supplier price with its source provider.
No provider-failure banner is shown.

### 2. Pagination with the opaque cursor

**API:**
1. Request a small page:
   ```bash
   curl "http://localhost:8080/api/flights/search?origin=MAD&destination=JFK&departureDate=2026-10-01&pageSize=2"
   ```
2. Copy the `nextCursor` value from the response and pass it back unchanged:
   ```bash
   curl "http://localhost:8080/api/flights/search?origin=MAD&destination=JFK&departureDate=2026-10-01&pageSize=2&cursor=<nextCursor>"
   ```

Expected: the second page returns the next set of itineraries in the same stable order
(departure time, selling price, materialized ID), with no duplicates or gaps versus the
first page. This verifies the PostgreSQL keyset pagination.

**UX:** Repeat the happy-path search but set **Page size** to `2` before clicking
**Search flights**. Expected: only two flight cards are rendered and a **Next page**
button appears below the results (it stays hidden whenever there is no cursor, e.g. on
the unfiltered happy-path search if it fits in one page). Click **Next page**: the
button is not replaced but the newly returned cards are **appended** below the existing
ones, the result counter increases accordingly, and the previously shown cards remain
unchanged and in order. Clicking **Next page** repeatedly should keep appending distinct
cards until no more pages remain, at which point the button disappears again.

### 3. Filtering by maximum price and carrier

**API:**
```bash
curl "http://localhost:8080/api/flights/search?origin=MAD&destination=JFK&departureDate=2026-10-01&maxPrice=200&carrier=OA"
```

Expected: only itineraries with `sellingPrice <= 200` and a matching `carrier` are
returned. Repeat the request without `maxPrice`/`carrier` and confirm more results come
back, proving the filters are applied.

**UX:** Perform the happy-path search once with **Maximum price** set to `200` and
**Carrier** set to `OA`, and once with both fields left empty. Expected: the filtered
search shows a smaller (or equal) result count, and every visible card's selling price is
at or below the configured maximum and shows the requested carrier in its segments; the
unfiltered search shows a result count that is greater than or equal to the filtered one.
Typing an invalid carrier (e.g. a single letter) or a negative price and clicking
**Search flights** should not call the API at all: the status line below the button
turns into an inline validation error ("Carrier must be a two- or three-letter code." /
"Maximum price must not be negative.") shown in red, and no results section is rendered.

### 4. Partial provider failure (`origin=ERR`)

**API:**
```bash
curl "http://localhost:8080/api/flights/search?origin=ERR&destination=JFK&departureDate=2026-10-01"
```

Expected: `200 OK`, not `500`. Mock-Alpha returns HTTP 500 in this scenario, but the
response still contains the itineraries from Mock-Beta/Mock-Gamma plus a populated
`providerFailures` entry describing the Alpha failure. This is also covered
automatically by `ProviderResiliencyTest`.

**UX:** In the web form, type `ERR` in **Origin** instead of `MAD` and submit. Expected:
the page still renders flight cards (from Mock-Beta/Mock-Gamma) and, above the flight
list, a **"Partial results: ..."** warning line appears naming the failed provider and
its reason. The page does not show a hard error and the **Search flights** button
returns to its normal enabled state.

### 5. Provider timeout isolation (`origin=SLO`)

**API:**
```bash
curl "http://localhost:8080/api/flights/search?origin=SLO&destination=JFK&departureDate=2026-10-01"
```

Expected: the response returns in ~3 seconds (the configured provider timeout), not the
5 seconds Mock-Gamma takes to answer in this scenario. The result contains Alpha/Beta
itineraries and a `providerFailures` entry for the timed-out provider.

**UX:** Type `SLO` in **Origin** and submit. Expected: the button shows "Searching..."
for roughly 3 seconds (not 5), then results render with Alpha/Beta flights and the same
"Partial results: ..." warning naming the timed-out provider, confirming from the UI that
a slow provider cannot block the whole page from loading.

### 6. Redis cache hit vs. miss

1. Issue a search and note the response time (cache miss, queries all providers).
2. Repeat the exact same request (same origin, destination, departure date, `maxPrice`,
   and `carrier`) within 5 minutes.

Expected: the second call is noticeably faster and returns an identical payload, because
`CachedSearchFlights` served it from Redis without calling the providers again. Check the
application logs for the `CachedSearchFlights` hit/miss entries. Changing any of the
cache-key parameters (e.g. adding `maxPrice`) forces a fresh cache miss.

**UX:** Submit the happy-path search from the form and observe how long the "Searching..."
label stays on the button before results render. Submit the identical form again (same
field values) without changing anything: the "Searching..." label should disappear almost
instantly and render the same cards/order as before, showing the perceived speed-up from
the cache to a non-technical evaluator. Changing **Maximum price** or **Carrier** before
resubmitting should bring back the slower, multi-provider response time.

### 7. Validation and error handling

**API:**
```bash
curl -i "http://localhost:8080/api/flights/search?origin=MAD&destination=JFK"
curl -i "http://localhost:8080/api/flights/search?origin=MAD&destination=JFK&departureDate=2026-10-01&cursor=not-a-valid-cursor"
```

Expected: both requests return `400 Bad Request` with an explanatory JSON error body
(missing `departureDate` and a malformed cursor, respectively), produced by
`ApiExceptionHandler` and logged at `WARN`.

**UX:** In the form, leave **Departure date** empty and click **Search flights**.
Expected: the client-side check catches this before calling the API and shows the inline
error "Select a departure date." in red under the form; nothing is sent to the server.
Typing a 2-letter code in **Origin**/**Destination** shows "Origin and destination must
be three-letter airport codes." the same way. To see a *server-side* `400` reflected in
the UI, use the browser dev tools' network tab (or a `curl -i` call) with a malformed
`cursor`, since the form itself never builds an invalid cursor: the response body's
`message` is the same text that `ApiExceptionHandler` returns, and if it were surfaced
through the form flow it would appear as the red status message returned by the
`catch` block in the page's script.

### 8. Web interface

Open `http://localhost:8080/` in a browser, fill in origin/destination/date and,
optionally, max price/carrier, and submit the form. Expected: the page renders the
returned itineraries and shows a **Next page** button only while a cursor is available;
clicking it appends results while preserving order.

**UX walkthrough:**
1. On page load, **Departure date** is pre-filled with today's date and the results
   section is hidden.
2. Fill **Origin**/**Destination** with valid 3-letter codes and click **Search
   flights**. The button becomes disabled and reads "Searching...".
3. When the response arrives, the **Available flights** heading and a result counter
   (e.g. "5 results") appear, followed by one card per itinerary with route, segments,
   selling price, and supplier price/provider.
4. If no itineraries match, an "No flights were found for this route." message is shown
   instead of cards.
5. If the API returned provider failures, a "Partial results: ..." line is shown above
   the cards.
6. If a `nextCursor` was returned, the **Next page** button is visible; clicking it
   fetches and appends the next page without clearing the current cards. If there is no
   further page, the button stays hidden.

### 9. API documentation

Open `http://localhost:8080/swagger-ui/index.html` and try the `GET
/api/flights/search` operation directly from the UI ("Try it out"). Expected: the
generated OpenAPI document matches the controller's parameters and responses, and
requests issued from Swagger UI behave the same as the `curl` examples above.

**UX:** Expand the `GET /api/flights/search` operation, click **Try it out**, fill in
`origin`, `destination`, and `departureDate` (and optionally `maxPrice`/`carrier`/
`pageSize`/`cursor`), then click **Execute**. Expected: Swagger UI shows the request URL
it built, the response status code, headers, and the same JSON body an evaluator would
get from `curl`, letting a non-technical reviewer explore the API without a terminal.

### 10. Health and observability

```bash
curl "http://localhost:8080/actuator/health"
```

Expected: `status: UP`, with PostgreSQL and Redis reported as up. Stopping the Redis or
PostgreSQL container should flip this to `DOWN`, demonstrating that health reflects real
dependency connectivity.

**UX:** Open `http://localhost:8080/actuator/health` directly in a browser tab. Expected:
the browser renders the raw JSON with `"status": "UP"` and nested `components` for `db`
and `redis`, each also `"UP"`; this gives a quick visual signal (without any tooling)
that the dependent containers are reachable before running the other use cases above.
## Scaling considerations

The materialized read model, Redis result cache, concurrent provider calls, JDBC insert
batching, and keyset traversal are the foundation for the required daily inventory
scale. The default suite stays fast, while the dedicated Testcontainers volume profile
materializes and reads 100,000 offers through the real PostgreSQL adapter:

```bash
mvn -Pvolume-test test
```

The profile accepts `-Dflight.volume.rows=1000000` to exercise the upper inventory
target. It verifies that the materialized store returns successive keyset pages without
duplicates; it never uses a growing SQL offset. A production deployment should add
scheduled ingestion for high-demand routes, retention and archival rules for
materialized offers, metrics/alerting, and scheduled performance runs at the expected
volume.

## Known limitations and production hardening backlog

This solution intentionally favors "keep it simple" for the scope and timeline of the
assessment. Logging, Actuator health/metrics, OpenAPI documentation, and a centralized
exception handler are implemented; the following are consciously deferred and would be
required before running this system in production:

- **Schema migrations.** `spring.jpa.hibernate.ddl-auto=update` is convenient for local
  development but unsafe for production. A real deployment should use Flyway or
  Liquibase with versioned, reviewable migration scripts and disable Hibernate's
  auto-DDL entirely.
- **Resilience beyond timeouts.** Each provider call has a fixed timeout, but there is
  no circuit breaker, retry with backoff, or bulkhead isolation (e.g. Resilience4j).
  Under sustained provider degradation, every request still pays the timeout cost
  instead of failing fast.
- **Security.** The API has no authentication/authorization, no rate limiting, and no
  explicit CORS policy. A production OTA-facing API would need at least API keys or
  OAuth2, per-client rate limits, and an explicit CORS configuration for the frontend
  origin.
- **Materialized data retention.** `materialized_flights` is upserted indefinitely with
  no TTL, archival, or cleanup job. Production would need a retention policy (e.g.
  drop itineraries once their departure date has passed) to bound storage growth at
  the target scale of 100,000–1,000,000 daily combinations.
- **API documentation depth.** OpenAPI is generated from controller annotations;
  response schemas, error examples, and versioning strategy could be further detailed.
- **CI/CD.** There is no automated pipeline running `mvn test`, building the Docker
  image, or deploying it. A production setup should run the full test suite (including
  the Testcontainers-based integration tests) on every push.
- **Secrets management.** Database and cache credentials are defined directly in
  `compose.yaml` for local development convenience. Production should source them from
  a secrets manager or environment-specific vault, never from version control.
- **Load-test environment tuning.** The `volume-test` profile provides a reproducible
  100,000–1,000,000 row verification, but production database instance sizing,
  connection-pool tuning, and traffic characteristics must still be measured in the
  target deployment environment.

## AI tools used

GitHub Copilot was used as an interactive coding assistant. No autonomous agent system
or external code-generation service was used. The developer retained responsibility for
the architecture, code review, and validation.

The assistant was used to discuss the Clean Architecture boundaries, propose the domain
model and naming, implement and review provider adapters and tests, validate resiliency
and pagination behavior, and identify the delivery gaps documented above. All generated
changes were reviewed locally with the Maven test suite and the Dockerized application.

Final decisions were reviewed manually and validated through compilation and tests with Java 25.
