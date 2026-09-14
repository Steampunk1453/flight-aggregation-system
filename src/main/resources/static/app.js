const form = document.querySelector("#search-form");
const originInput = document.querySelector("#origin");
const destinationInput = document.querySelector("#destination");
const departureDateInput = document.querySelector("#departure-date");
const status = document.querySelector("#search-status");
const resultsSection = document.querySelector("#results-section");
const resultCount = document.querySelector("#result-count");
const flightResults = document.querySelector("#flight-results");
const providerFailures = document.querySelector("#provider-failures");
const submitButton = form.querySelector("button");

departureDateInput.value = new Date().toISOString().slice(0, 10);

form.addEventListener("submit", async (event) => {
    event.preventDefault();

    const origin = originInput.value.trim().toUpperCase();
    const destination = destinationInput.value.trim().toUpperCase();
    const departureDate = departureDateInput.value;

    if (!isAirportCode(origin) || !isAirportCode(destination)) {
        showError("Origin and destination must be three-letter airport codes.");
        return;
    }
    if (!departureDate) {
        showError("Select a departure date.");
        return;
    }

    setLoading(true);
    clearResults();
    try {
        const params = new URLSearchParams({origin, destination, departureDate});
        const response = await fetch(`/api/flights/search?${params}`);
        const body = await response.json();
        if (!response.ok) {
            throw new Error(body.message || "The flight search could not be completed.");
        }
        renderResults(body);
        status.textContent = "";
    } catch (error) {
        showError(error instanceof Error ? error.message : "The flight search could not be completed.");
    } finally {
        setLoading(false);
    }
});

function isAirportCode(value) {
    return /^[A-Z]{3}$/.test(value);
}

function setLoading(isLoading) {
    submitButton.disabled = isLoading;
    submitButton.textContent = isLoading ? "Searching..." : "Search flights";
    if (isLoading) {
        status.className = "status";
        status.textContent = "Searching providers...";
    }
}

function clearResults() {
    resultsSection.hidden = true;
    flightResults.replaceChildren();
    providerFailures.replaceChildren();
}

function showError(message) {
    status.className = "status error";
    status.textContent = message;
}

function renderResults(result) {
    resultsSection.hidden = false;
    resultCount.textContent = `${result.flights.length} result${result.flights.length === 1 ? "" : "s"}`;

    if (result.flights.length === 0) {
        const emptyState = document.createElement("p");
        emptyState.className = "empty-state";
        emptyState.textContent = "No flights were found for this route.";
        flightResults.append(emptyState);
    } else {
        result.flights.forEach((flight) => flightResults.append(createFlightCard(flight)));
    }

    if (result.providerFailures.length > 0) {
        const warning = document.createElement("p");
        warning.className = "provider-warning";
        warning.textContent = `Partial results: ${result.providerFailures
            .map((failure) => `${failure.provider} (${failure.reason})`)
            .join(", ")}.`;
        providerFailures.append(warning);
    }
}

function createFlightCard(flight) {
    const card = document.createElement("article");
    card.className = "flight-card";

    const route = document.createElement("h3");
    route.textContent = `${flight.segments[0].origin} to ${flight.segments.at(-1).destination}`;

    const segments = document.createElement("div");
    segments.className = "segments";
    flight.segments.forEach((segment) => {
        const segmentRow = document.createElement("p");
        segmentRow.textContent = `${segment.carrierCode} ${segment.flightNumber} (${segment.carrierName}) · `
            + `${formatDateTime(segment.departureAt)} - ${formatDateTime(segment.arrivalAt)}`;
        segments.append(segmentRow);
    });

    const price = document.createElement("p");
    price.className = "price";
    price.textContent = formatMoney(flight.sellingPrice);

    const supplierPrice = document.createElement("p");
    supplierPrice.className = "supplier-price";
    supplierPrice.textContent = `Supplier price: ${formatMoney(flight.supplierPrice)} · ${flight.provider}`;

    card.append(route, segments, price, supplierPrice);
    return card;
}

function formatDateTime(value) {
    return new Intl.DateTimeFormat(undefined, {
        dateStyle: "medium",
        timeStyle: "short"
    }).format(new Date(value));
}

function formatMoney(money) {
    return new Intl.NumberFormat(undefined, {
        style: "currency",
        currency: money.currency
    }).format(money.amount);
}
