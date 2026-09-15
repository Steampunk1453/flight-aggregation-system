# ✈️ Flight Aggregation System

A resilient, high-volume REST API built to concurrently aggregate, deduplicate, and paginate flight data from multiple GDS providers. 

Built with **Java 25**, **Spring Boot**, **PostgreSQL**, and **Redis** following strict **Clean Architecture** principles.

---

## 📖 Technical Documentation & System Design

👉 **[Read the full SOLUTION.md](SOLUTION.md)**

All architectural decisions have been detailed in the `SOLUTION.md` file. There you will find a deep dive into:
- Architectural trade-offs and strict Domain boundary enforcement.
- Concurrency, timeouts, and Graceful Degradation (`CompletableFuture`).
- High-volume data handling via Keyset Pagination (Cursor-based).
- AI Assistance transparency.

---

## 🚀 Quick Start

### Prerequisites
- Java 25
- Docker Desktop with Docker Compose

### Run the stack
```bash
# Start the application, databases, and all GDS mock providers
docker compose up --build
