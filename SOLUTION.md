# Flight Aggregation & Cache Optimization System

## 1. Quick start

### Prerequisites

- Java 25
- Docker Desktop with Docker Compose
- Maven is provided by the included Maven Wrapper.

### Run the complete stack

```bash
docker compose up --build
```

```bash
# Stop the stack and remove database/cache volumes
docker compose down -v
```

| Service | Address |
|---|---|
| Application and frontend | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui/index.html |
| Health endpoint | http://localhost:8080/actuator/health |
| Mock Alpha | http://localhost:8081/__admin |
| Mock Beta | http://localhost:8082/__admin |
| Mock Gamma | http://localhost:8083/__admin |

Open `http://localhost:8080/`, search `MAD` to `JFK` for `2026-10-01`, and use
`pageSize=2` to exercise cursor pagination. If the backend is already running locally,
stop it before starting the complete Docker stack because both use port `8080`.

### Run tests

```bash
# Unit and integration tests
./mvnw test

# Dedicated PostgreSQL volume test: 100,000 offers by default
./mvnw -Pvolume-test test

# **Extreme volume test (materializes 1,000,000 offers and tests keyset traversal)**
./mvnw -Pvolume-test -Dflight.volume.rows=1000000 test

# Generate the JaCoCo HTML coverage report at target/site/jacoco/index.html
./mvnw verify
```

The volume profile is intentionally excluded from the standard suite. It needs Docker
storage sized for the selected inventory volume. On Windows, replace `./mvnw` with
`mvnw.cmd`.

## 2. Requirement coverage

| Requirement | Implementation |
|---|---|
| Three GDS providers in parallel | `SearchFlightsUseCase` invokes Alpha, Beta, and Gamma concurrently with a shared virtual-thread executor. |
| Legacy formats | Alpha parses JSON, Beta parses nested XML, and Gamma parses CSV streams. |
| Deduplication | Ordered segments, airline, flight number, route, and UTC departure time form the itinerary key. The cheapest supplier offer wins. |
| Invalid legs and connections | `FlightSegment` rejects dead legs and invalid times; `FlightItinerary` validates each connection. Adapters skip invalid records while keeping valid records from the same response. |
| 5% OTA markup | `FixedMarkupPolicy` applies the configured `flight.pricing.markup-percentage`, defaulting to 5%. |
| Search filters | Origin, destination, departure date, maximum selling price, and carrier are supported. Carrier matches any itinerary segment. |
| Pagination | PostgreSQL keyset pagination uses an opaque cursor containing departure time, selling price, and ID. |
| Frontend | The static dashboard submits searches, shows results and partial failures, and appends cursor pages. |
| Provider resilience | Per-provider timeouts and controlled failures return partial results instead of failing the complete search. |
| Docker | `docker compose up --build` starts the application, PostgreSQL, Redis, and all mock providers. |

## 3. Architecture

```text
Browser / REST API
        |
SearchPagedFlightsUseCase
        |
SearchFlights (cache decorator)
        |
SearchFlightsUseCase
        |
FlightSearchProvider (Alpha, Beta, Gamma adapters)
        |
FlightSearchResultStore (PostgreSQL read model)
```

The project follows Clean Architecture:

| Layer | Responsibility |
|---|---|
| `domain` | Immutable value objects and business policies: itinerary identity, connection validity, money, and markup. |
| `application` | Use cases, DTOs, and input/output ports. It orchestrates searches without depending on Spring, HTTP, Redis, or JPA. |
| `infrastructure` | Spring configuration, HTTP provider adapters, Redis cache, PostgreSQL/JPA adapter, web API, and static frontend. |

ArchUnit tests enforce that domain code does not depend on application or infrastructure,
and application code does not depend on infrastructure.

### Main flow

```text
GET /api/flights/search
  -> validate filters and cursor
  -> read cached or live aggregate result
  -> query all providers concurrently on a cache miss
  -> normalize to FlightItinerary
  -> reject invalid records, deduplicate, select lowest supplier price
  -> apply markup and materialize offers
  -> query the requested keyset page
  -> return flights, provider failures, and nextCursor
```

## 4. Domain and aggregation decisions

### Provider normalization

All provider responses become `FlightItinerary` instances before entering the
application use case:

- **Mock Alpha:** JSON list. A direct-flight object or an object with a `segments`
  array is supported.
- **Mock Beta:** nested XML. A direct `<flight>` or nested `<segments>` is supported.
- **Mock Gamma:** newline-separated CSV records. The original nine-column direct-flight
  format is supported; an optional tenth itinerary ID groups segment records into a
  connecting itinerary.

Malformed payloads are provider failures. A syntactically valid payload containing an
invalid itinerary record does not discard the provider's other valid itineraries.

### Deduplication and price

`FlightItinerary.deduplicationKey()` combines, in segment order:

1. Carrier code and flight number.
2. Segment origin and destination.
3. Departure instant normalized to UTC.

Provider and price are intentionally excluded: they describe the commercial offer, not
the physical itinerary. Arrival time is also excluded because the stated identity is
based on flight numbers, connections, and departure times.

`FlightOffer` preserves both prices:

```text
supplierPrice = GDS offer price
sellingPrice  = supplierPrice * 1.05
```

The markup is a `MarkupPolicy` in the domain. The current `FixedMarkupPolicy` can be
replaced by carrier-, route-, or campaign-specific policies without changing the search
use case.

### Resilience

Every provider request has an independent configurable timeout (`PT3S` by default).
Provider HTTP errors, parse failures, and timeouts become `ProviderSearchFailure`
entries. Healthy provider responses remain visible.

The WireMock scenarios make this behavior reproducible:

| Scenario | Behavior |
|---|---|
| `origin=ERR` | Alpha returns HTTP 500; Beta and Gamma results are returned. |
| `origin=SLO` | Gamma delays for 5 seconds; the search returns Alpha/Beta partial results after the 3-second timeout. |

## 5. API and frontend

```http
GET /api/flights/search?origin=MAD&destination=JFK&departureDate=2026-10-01&maxPrice=200&carrier=OA&pageSize=20&cursor=<opaque-cursor>
```

| Parameter | Required | Notes |
|---|---|---|
| `origin`, `destination` | Yes | Three-letter airport codes. |
| `departureDate` | Yes | ISO-8601 date. |
| `maxPrice` | No | Maximum OTA selling price. |
| `carrier` | No | Two- or three-letter carrier code, matched against every segment. |
| `pageSize` | No | Defaults to 20; valid range is 1-100. |
| `cursor` | No | Opaque value returned in `nextCursor`. |

The response contains `flights`, `providerFailures`, and `nextCursor`. Invalid criteria
or cursors return `400 Bad Request`; unexpected errors return a generic `500` response.
All HTTP errors use the same JSON structure: `timestamp`, `status`, `code`, `message`,
and `path`. Provider failures remain part of a successful partial-search response rather
than HTTP errors.

The frontend at `/` provides the same filters, client-side validation, partial-result
warnings, and a **Next page** button that appends the next cursor page.

## 6. High-volume design

The solution is designed for 100,000 to 1,000,000 daily combinations through:

### Database choice: PostgreSQL (SQL)

Although large OTAs often use NoSQL key-value stores such as Redis or Couchbase as
read-through caches for extreme read loads, PostgreSQL is the primary read model for
this aggregator. Modern SQL engines handle the stated one-million-combination inventory
profile, while PostgreSQL B-tree indexes fit the required multi-parameter filtering by
origin, destination, carrier, and maximum price. They also support the ordered seeks
needed for efficient keyset pagination.

Redis remains the short-lived aggregate-result cache. A document or key-value store as
the primary query store would make price range filters and cursor-based ordering more
complex than the indexed relational model.

- **Materialized PostgreSQL read model.** Repeated searches refresh deterministic
  itinerary rows instead of adding duplicates.
- **JDBC batching.** Hibernate batches inserts and updates in groups of 100.
- **Explicit transaction boundaries.** Materialization uses a write transaction, while
  keyset reads use `@Transactional(readOnly = true)` to make their read-only intent
  explicit and avoid unnecessary persistence-context flushing.
- **Redis cache.** A five-minute cache stores live aggregate results. Its key includes
  normalized origin, destination, date, maximum price, and carrier.
- **Keyset pagination.** Queries request `pageSize + 1` rows, ordered by
  `(departureAt, sellingAmount, id)`, and never use a growing SQL offset.
- **Supporting indexes.** `materialized_flights` indexes
  `(origin, destination, departureAt, sellingAmount, id)`. The segment table indexes
  `(carrierCode, flight_id)` for carrier filtering over all itinerary legs.

The `volume-test` profile materializes the configured number of offers through the real
PostgreSQL adapter and verifies consecutive keyset pages have no overlap. It defaults to
100,000 rows and accepts 1,000,000 with `-Dflight.volume.rows=1000000`.

## 7. Testing strategy

| Test type | Coverage |
|---|---|
| Domain unit tests | Money, carrier and segment validation, connection validation, deduplication key, and markup. |
| Application unit tests | Concurrent aggregation, cheapest duplicate selection, price/carrier filters, all-provider failure, and page materialization orchestration. |
| Adapter unit tests | JSON/XML/CSV parsing, connecting itineraries, invalid-record rejection, HTTP failure translation, cache keys, and cursor encoding. |
| Architecture tests | Layer dependency rules enforced with ArchUnit. |
| Integration tests | Redis and PostgreSQL persistence with Testcontainers, provider HTTP resiliency, and complete MVC-to-provider/cache/database flow. |
| Volume profile | 100,000 to 1,000,000 PostgreSQL offers with real keyset traversal. |

## 8. Operations and observability

`application.yml` configures provider endpoints, timeout, markup, Redis cache TTL,
database connection, JDBC batching, and log levels. Docker Compose provides development
credentials and service discovery through Docker service names.

Spring Boot Actuator exposes:

```text
GET /actuator/health
GET /actuator/info
GET /actuator/metrics
```

SLF4J logs incoming searches, cache hits/misses, provider calls, partial failures, and
unexpected web errors. OpenAPI documentation is available at `/v3/api-docs` and Swagger
UI at `/swagger-ui/index.html`.

## 9. Known limitations and production hardening

- **Schema migrations:** `ddl-auto=update` is appropriate for local development only;
  production should use Flyway or Liquibase.
- **Provider resilience:** circuit breakers, retries with backoff, and bulkheads are
  not implemented.
- **Retention:** materialized offers have no cleanup policy for departed flights.
- **Security:** authentication, authorization, rate limiting, and an explicit CORS
  policy are not included.
- **Volume environment:** the included profile is reproducible, but instance sizing,
  connection-pool tuning, and traffic patterns must be measured in the target
  production environment.
- **CI/CD:** no pipeline currently runs tests, builds the image, or deploys the service.

## 10. AI assistance

GitHub Copilot was used as an interactive coding assistant. No autonomous agent system
or external code-generation service was used. Architectural and implementation decisions
remained under developer review.

To illustrate the collaboration model, these are representative prompts used during
development:

- **Architecture & trade-offs:** *"I need to handle GDS mock providers that have up
  to 5s latency, but I want to keep the search fast. What are the trade offs of
  implementing a strict 3 second timeout and returning partial results?"*
- **Data Aggregation**: *"How can I group a list of flights by a custom string key, resolve duplicates by keeping the
   one with the lowest price, and then map the resulting map's values to a new list?"*
- **Database optimization:** *"Explain the exact mechanism of Keyset Pagination
  (cursor-based) in PostgreSQL compared to standard OFFSET. How should I encode the
  cursor for a search result ordered by price and departure time?"*
