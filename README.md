# Seat Reservation Service

A concurrency-safe assigned-seat reservation service built with Java, Spring Boot, and PostgreSQL.

The service handles concurrent booking scenarios where multiple users may try to reserve the same seat at the same time.

It provides APIs for:

- Creating shows with assigned-seat inventory
- Reserving one or more seats atomically
- Enforcing per-user booking limits
- Handling reservation retries using idempotency keys
- Cancelling reservations and releasing seats safely
- Viewing current seat inventory and reservation state
- Exposing health checks and Prometheus metrics

## Core Guarantees

The implementation provides the following guarantees:

- A seat cannot be confirmed for more than one reservation.
- Multi-seat reservations are all-or-nothing.
- The per-user booking limit is enforced under concurrent requests.
- Retrying a reservation with the same idempotency key does not create another reservation.
- Reusing an idempotency key with a different request is rejected.
- Cancellation can only be performed by the reservation owner.
- Released seats become available for future reservations.
- Seat inventory remains consistent with the show-level inventory counts.

## Live Deployment

The service is deployed on Railway:

**Base URL:** `https://seat-reservation-service-production-f9d1.up.railway.app`

Health endpoints:

- Liveness: `/actuator/health/liveness`
- Readiness: `/actuator/health/readiness`
- Prometheus metrics: `/actuator/prometheus`

## Tech Stack

- Java 21
- Spring Boot 4
- Spring Web MVC
- Spring Data JPA / Hibernate
- PostgreSQL
- Flyway for database migrations
- Spring Boot Actuator
- Micrometer + Prometheus
- Testcontainers for PostgreSQL integration testing
- JUnit
- Docker / Docker Compose
- k6 for load testing
- Railway for deployment

## Architecture

The application is implemented as a modular monolith with PostgreSQL as the source of truth for reservation state.

```text
Client
  |
  v
REST Controller
  |
  v
Reservation Admission Control
  |
  v
Transactional Service Layer
  |
  +--> Idempotency Lock
  |
  +--> Per-User Booking Lock
  |
  +--> Seat Row Locks
  |
  v
PostgreSQL
```

Reservation requests pass through application-level admission control before entering the transactional reservation flow.

Admission control limits pressure on the database, while the actual reservation decision is protected by PostgreSQL transactions, locks, and constraints.

For reservations, database locks are acquired in the following order:

1. Idempotency record
2. Show/user booking record
3. Requested seats in sorted order

Reservation seat locking uses pessimistic locking with fail-fast contention behavior so that competing requests for a hot seat do not create a long database lock queue.

Cancellation also uses pessimistic locking, but allows the transaction to wait for the relevant seat locks before validating ownership and releasing the seats safely.

## Running Locally

### Prerequisites

The easiest way to run the complete application locally is with Docker Compose.

Required:

- Docker
- Docker Compose

### Start the Application

From the project root:

```bash
docker compose up --build
```

Docker Compose starts:

- The Spring Boot application
- PostgreSQL
- Database migrations through Flyway

Once startup completes, the application is available at:

```text
http://localhost:8080
```

Verify readiness:

```bash
curl http://localhost:8080/actuator/health/readiness
```

Expected response:

```json
{
  "status": "UP"
}
```

### Stop the Application

```bash
docker compose down
```

To also remove the PostgreSQL volume and start with a fresh database:

```bash
docker compose down -v
```

### Run Without Docker

If PostgreSQL is already available locally, configure the following environment variables:

```text
DB_URL=jdbc:postgresql://localhost:5432/seat_reservation
DB_USERNAME=<username>
DB_PASSWORD=<password>
```

Then run:

```bash
./mvnw spring-boot:run
```

On Windows:

```powershell
.\mvnw.cmd spring-boot:run
```

Flyway automatically applies the required database migrations during application startup.

## API Documentation

The examples below use:

```text
http://localhost:8080
```

For the deployed service, replace this with the Railway base URL.

### 1. Create a Show

**Endpoint**

```http
POST /shows
```

**Request**

```bash
curl -X POST http://localhost:8080/shows \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Friday Night Show",
    "seats": ["A1", "A2", "A3", "A4", "A5", "A6"],
    "price_paise": 50000
  }'
```

Example response:

```json
{
  "id": "<show-id>",
  "name": "Friday Night Show",
  "price_paise": 50000,
  "per_user_limit": 4,
  "seats": [
    {
      "seat": "A1",
      "status": "available"
    },
    {
      "seat": "A2",
      "status": "available"
    },
    {
      "seat": "A3",
      "status": "available"
    },
    {
      "seat": "A4",
      "status": "available"
    },
    {
      "seat": "A5",
      "status": "available"
    },
    {
      "seat": "A6",
      "status": "available"
    }
  ]
}
```

Save the returned show ID for the following requests.

### 2. Reserve Seats

Reservation endpoints use a Bearer token as the user identity.

For this take-home implementation, the Bearer token value itself represents the authenticated user ID.

**Endpoint**

```http
POST /shows/{showId}/reserve
```

**Request**

```bash
curl -X POST http://localhost:8080/shows/<show-id>/reserve \
  -H "Authorization: Bearer user-123" \
  -H "Content-Type: application/json" \
  -d '{
    "seats": ["A1", "A2"],
    "idempotency_key": "booking-001"
  }'
```

A successful reservation returns HTTP `201 Created`.

Example response:

```json
{
  "reservation_id": "<reservation-id>",
  "show_id": "<show-id>",
  "user_id": "user-123",
  "seats": [
    "A1",
    "A2"
  ],
  "amount_paise": 100000,
  "status": "confirmed"
}
```

If another user attempts to reserve an already-confirmed seat, the request is rejected with HTTP `409 Conflict`.

#### Idempotency

Repeating the same reservation request with the same user and `idempotency_key` returns the existing reservation instead of creating another reservation.

Reusing the same idempotency key with a different reservation request returns HTTP `409 Conflict`.

### 3. View Show Inventory

**Endpoint**

```http
GET /shows/{showId}
```

**Request**

```bash
curl http://localhost:8080/shows/<show-id>
```

Example response after reserving `A1` and `A2`:

```json
{
  "id": "<show-id>",
  "name": "Friday Night Show",
  "price_paise": 50000,
  "per_user_limit": 4,
  "seats": [
    {
      "seat": "A1",
      "status": "confirmed"
    },
    {
      "seat": "A2",
      "status": "confirmed"
    },
    {
      "seat": "A3",
      "status": "available"
    },
    {
      "seat": "A4",
      "status": "available"
    },
    {
      "seat": "A5",
      "status": "available"
    },
    {
      "seat": "A6",
      "status": "available"
    }
  ],
  "counts": {
    "available": 4,
    "held": 0,
    "confirmed": 2,
    "total": 6
  }
}
```

The inventory maintains the following invariant:

```text
available + held + confirmed = total
```

This implementation uses immediate confirmation and explicit cancellation rather than temporary holds, so `held` is currently always `0`.

### 4. Cancel a Reservation

Only the user who owns the reservation can cancel it.

**Endpoint**

```http
POST /reservations/{reservationId}/cancel
```

**Request**

```bash
curl -X POST http://localhost:8080/reservations/<reservation-id>/cancel \
  -H "Authorization: Bearer user-123"
```

Cancellation releases the reservation's seats, making them available for future reservations.

Repeated cancellation of the same reservation is idempotent.

## Authentication

Reservation and cancellation endpoints require an `Authorization` header:

```http
Authorization: Bearer <user-id>
```

For this take-home implementation, the Bearer token value is treated as the authenticated user identity.

The user identity comes from the authentication header rather than the request body, so callers cannot select another user identity through the reservation payload.

Example:

```http
Authorization: Bearer user-123
```

This is intentionally lightweight authentication for the scope of the exercise. In a production system, it would be replaced with proper authentication such as JWT/OAuth2 while continuing to keep user identity outside the request body.

## Health & Observability

The application exposes Spring Boot Actuator endpoints for health monitoring and Prometheus-compatible metrics.

### Health Checks

Liveness:

```http
GET /actuator/health/liveness
```

Readiness:

```http
GET /actuator/health/readiness
```

Readiness includes database connectivity. If PostgreSQL is unavailable, the application reports itself as not ready because reservation requests cannot be processed safely without checking the current database state.

### Prometheus Metrics

Metrics are exposed at:

```http
GET /actuator/prometheus
```

Reservation-specific metrics include:

```text
reservation_confirmed_total
reservation_declined_total{reason="seat_taken"}
reservation_declined_total{reason="per_user_limit"}
reservation_declined_total{reason="idempotent_replay"}
seats_available{show_id="<show-id>"}
```

These metrics provide visibility into successful reservations, expected reservation declines, idempotent retries, and current seat availability.

### Structured Logging

Application logs are emitted as structured JSON and include request correlation IDs.

Clients may optionally provide:

```http
X-Request-ID: <correlation-id>
```

If no request ID is supplied, the application generates one. The request ID is also returned in the response header and included in application logs, making it easier to trace a request through the service.

Business events include reservation confirmation, reservation declines, idempotent replays, show creation, and cancellation.

## Running Tests

The project includes unit tests and PostgreSQL-backed integration tests.

Run the complete test suite with:

```bash
./mvnw clean test
```

On Windows:

```powershell
.\mvnw.cmd clean test
```

Integration tests use Testcontainers with a real PostgreSQL instance rather than an in-memory database.

The test suite covers scenarios including:

- Successful seat reservation
- Idempotent reservation replay
- Idempotency-key conflict with a different request
- Per-user booking limit enforcement
- Multi-seat all-or-nothing behavior
- Concurrent hot-seat reservation
- Concurrent per-user limit enforcement
- Concurrent requests using the same idempotency key
- Reservation cancellation and seat release
- Repeated cancellation
- Reservation ownership validation
- Cancellation followed by rebooking
- Inventory reconciliation

## Load Testing

A k6 hot-seat contention test is included at:

```text
load-tests/hot-seat.js
```

The test creates a fresh show and sends:

```text
20,000 reservation attempts
100 concurrent virtual users
1 hot seat
```

Each reservation attempt uses a unique user and idempotency key.

Run against the deployed service:

```bash
k6 run -e BASE_URL=https://seat-reservation-service-production-f9d1.up.railway.app load-tests/hot-seat.js
```

Or against the local application:

```bash
k6 run -e BASE_URL=http://localhost:8080 load-tests/hot-seat.js
```

The script separately tracks:

```text
reservation_success
seat_taken_conflict
http_5xx_errors
network_errors
unexpected_status
```

It also performs final inventory reconciliation to verify that exactly one reservation owns the hot seat.

### Validated Load-Test Result

A 20,000-attempt run against the Railway deployment with 100 concurrent virtual users produced:

```text
Confirmed reservations : 1
Seat-taken conflicts    : 19,981

Client network errors: 18
    ---> (This is occurring ecause of network error, on Render this count is 7 ut its taking 
           around 12 min to process 20,000 loads, while on Railway - network_error count is 18
           and its taking very less time to process same load)
           
HTTP 5xx responses      : 0
Interrupted iterations  : 0
```

Final inventory reconciliation passed, confirming that exactly one reservation owned the hot seat and the inventory remained consistent.

The load test represents 20,000 reservation attempts executed by 100 concurrent virtual users; it should not be interpreted as 20,000 simultaneous connections.

## Concurrency & Correctness

Reservation correctness is enforced primarily through PostgreSQL transactions, row-level locking, and database constraints.

The main reservation lock order is:

```text
Idempotency record
        |
        v
Show/User booking record
        |
        v
Requested seats (sorted)
```

Requested seats are sorted before locking to reduce deadlock risk.

Reservation seat locking uses fail-fast pessimistic locking so that heavy contention on a hot seat does not create a long database lock queue.

An application-level admission control limits how many reservation requests enter the transactional path at once:

```text
RESERVATION_MAX_CONCURRENT
```

Admission control protects database capacity during traffic bursts, while PostgreSQL transactions, locks, and constraints protect booking correctness.

Multi-seat reservations are all-or-nothing, and the per-user booking limit is protected under concurrent requests.

Idempotency is scoped by user and idempotency key. Reusing the same key for the same request returns the existing reservation, while using it for a different request returns HTTP `409 Conflict`.

Cancellation is transactional, restricted to the reservation owner, and verifies current seat ownership before releasing inventory.

For the detailed locking strategy, deadlock reduction, idempotency behavior, cancellation flow, consistency trade-offs, and load-test findings, see [`WRITEUP.md`](WRITEUP.md).

## Design Scope

For this exercise:

- Reservations are confirmed immediately rather than temporarily held.
- `held` inventory therefore remains `0`.
- Seats are released through explicit owner cancellation.
- Authentication uses a lightweight Bearer-token identity mechanism.
- PostgreSQL is used for transactional consistency and concurrency control.
- Redis, Kafka, distributed locks, and Kubernetes are not required for the current implementation.

More detailed design decisions and trade-offs are documented in [`WRITEUP.md`](WRITEUP.md).

---------------

## What I Would Improve Next....

If I have to continue working on this activity as production ready system, my next work items would be:

- Replace the lightweight Bearer identity with JWT/OAuth2 and secure the admin endpoints.
- Store immutable idempotency response details if exact historical response replay is required.
- Add bounded admission waiting/backpressure for prolonged database pressure.
- A notification integration functionality (Email or Teams Channel) that will alert us during critical issues
- Add temporary seat holds and expiry only if the flow requires a checkout/payment window.