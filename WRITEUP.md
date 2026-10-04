# Seat Reservation Service - Design Write-up

## 1. Atomic Reservation Decision & Concurrency Control

The most important requirement for this service is to make sure that the same seat is never confirmed for more than one user, even when multiple users try to reserve it at the same time.

I am using PostgreSQL as the source of truth for seat availability and reservation state. The in-memory admission control is only used to control the load going to the database; it is not responsible for preventing double booking.

Each reservation is processed inside a single database transaction.

The reservation flow is:

```text
Reservation Request
        |
        v
Admission Control
        |
        v
Begin Database Transaction
        |
        v
Create / Lock Idempotency Record
        |
        v
Create / Lock Show-User Booking Record
        |
        v
Lock Requested Seat Rows
(sorted by seat number)
        |
        v
Validate Availability
        |
        v
Create Reservation
        |
        v
Create Reservation-Seat History
        |
        v
Mark Seats CONFIRMED
        |
        v
Increment User Booking Count
        |
        v
Attach Reservation to Idempotency Record
        |
        v
Commit
```

All the database changes required for a successful reservation happen within the same transaction. If any validation fails or an exception occurs, the transaction is rolled back so that we don't end up with a partially completed reservation.

### Seat-Level Concurrency Control

The requested seat rows are locked using PostgreSQL pessimistic write locks.

For reservation requests, I use fail-fast locking. If another transaction is already holding the lock for a requested seat, the new request does not keep waiting for that lock. Instead, the contention is handled as a conflict response.

This is useful for a hot-seat scenario where many users are trying to reserve the same seat at the same time.

The final decision about whether a seat can be reserved is always made using the current database state while the required seat rows are locked.

### Multi-Seat Atomicity

Multi-seat reservations use all-or-nothing semantics.

For example, if a user requests:

```text
A1, A2, A3
```

and `A2` is already unavailable, the service does not reserve only `A1` and `A3`.

The complete reservation transaction is rolled back, so either all requested seats are confirmed or none of them are.

### Per-User Limit Under Concurrency

The default booking limit is four seats per user per show.

Simply checking the user's current booking count is not enough because two concurrent requests from the same user could both read the same count and then exceed the limit.

To avoid this, each `(show, user)` pair has a booking record which is locked during reservation processing.

Because of this, even if the same user sends multiple reservation requests concurrently for different seats, those requests cannot independently update the booking count and exceed the configured limit.

### Database Constraints

Along with application-level locking, I also use database unique constraints for important data rules.

The important unique constraints are:

```text
(show_id, seat_number)
(show_id, user_id)
(user_id, idempotency_key)
(reservation_id, seat_number)
```

This gives an additional database-level safeguard instead of relying only on checks in the Java code.


----------------

## 2. Lock Ordering & Deadlock Reduction

For reservation requests, locks are acquired in a fixed order:

```text
1. Idempotency record
2. Show-user booking record
3. Requested seat rows, sorted by seat number
```

Sorting the requested seats is important when multiple requests overlap.

For example:

```text
Request 1: A1, A2
Request 2: A2, A1
```

If both requests lock seats in the order received from the client, one transaction could lock `A1` and wait for `A2`, while the other locks `A2` and waits for `A1`.

Before locking, I normalize and sort the seat numbers:

```text
Request 1 -> A1, A2
Request 2 -> A1, A2
```

This makes both transactions attempt the seat locks in the same order and reduces the chance of deadlocks. It does not mean database deadlocks are impossible, especially if more transaction flows are added later.

### Reservation vs Cancellation Locking

Reservation and cancellation handle lock contention slightly differently.

For reservation, seat locking is fail-fast because during a hot-seat event I do not want many transactions waiting on the same seat lock.

Cancellation still uses a pessimistic write lock, but it waits for the relevant seat lock and then checks the current ownership before releasing the seat. This is important because cancellation must never release a seat that no longer belongs to that reservation.


----------------

## 3. Admission Control

During load testing I found that fail-fast row locking alone was not enough to keep the application healthy.

A transaction needs a database connection before the reservation logic can reach the seat lock. During a burst of requests, many requests were waiting for HikariCP connections and the pool became exhausted. This resulted in connection-acquisition failures and HTTP 5xx responses.

To handle this, I added admission control before the transactional reservation service:

```text
HTTP Request
      |
      v
Admission Semaphore
      |
      v
Transactional Reservation Service
      |
      v
HikariCP
      |
      v
PostgreSQL
```

The maximum number of reservation requests allowed into the transactional path is configurable using:

```text
RESERVATION_MAX_CONCURRENT
```

I keep this value lower than the available database connection-pool capacity so that some connections remain available for health checks and other database operations.

The semaphore is intentionally outside the transactional service. A request waiting for admission therefore does not hold a database connection or transaction.

Admission control is only used for overload protection. It is not used to guarantee booking correctness. PostgreSQL locks, transactions and constraints still protect the reservation state.

The semaphore is also process-local. If multiple application instances are running, each instance would have its own admission limit, while PostgreSQL would continue to provide the shared correctness boundary.


----------------

## 4. Idempotency

Reservation requests contain an `idempotency_key` so that retrying the same request does not create another reservation.

The key is scoped using:

```text
(user_id, idempotency_key)
```

A unique database constraint prevents duplicate idempotency records for the same user and key.

### Request Fingerprint

The idempotency key by itself is not enough because a client could accidentally reuse the same key for a different reservation request.

For this reason, I generate a fingerprint using the show ID and the normalized seat selection. Seat numbers are trimmed and sorted before the fingerprint is created.

For example:

```text
Request 1:
show = S1
seats = [A1, A2]

Request 2:
show = S1
seats = [A2, A1]

-> Same logical request
-> Same fingerprint
```

The behavior is:

```text
Same user + same key + same fingerprint
-> Replay the existing reservation

Same user + same key + different fingerprint
-> 409 Conflict
```

### Concurrent Retries

Concurrent retries also need to be safe.

The idempotency record is created behind a database uniqueness constraint and then locked before reservation processing continues. This prevents two requests with the same user/key from independently creating two reservations.

The idempotency update and reservation are part of the same database transaction. If reservation processing fails and the transaction rolls back, the failed attempt does not leave a completed idempotency record behind.

### Replay Semantics

A retry returns the reservation already associated with the idempotency record instead of creating another reservation.

Currently the idempotency record points to the reservation rather than storing a separate immutable copy of the original HTTP response. Because of that, if the reservation is later cancelled, retrying the original key returns that same reservation in its current state.

If exact replay of the original HTTP response were required, I would store the original response/status with the idempotency record.


----------------

## 5. Cancellation, Release & Hold Semantics

For this implementation I chose immediate confirmation with explicit owner cancellation instead of temporary seat holds.

The seat lifecycle is:

```text
AVAILABLE
    |
    | successful reservation
    v
CONFIRMED
    |
    | owner cancellation
    v
AVAILABLE
```

The show API still returns the `held` count required by the inventory model, but it remains `0` because this implementation does not create temporary holds.

### Safe Cancellation

Cancellation is handled inside a database transaction.

The flow is:

```text
Lock Reservation
        |
        v
Verify Reservation Owner
        |
        v
Lock Show-User Booking Record
        |
        v
Lock Current Seat Rows
        |
        v
Verify Current Seat Ownership
        |
        v
Release Seats
        |
        v
Decrement User Booking Count
        |
        v
Mark Reservation CANCELLED
        |
        v
Commit
```

Before releasing a seat, the service checks that the seat still points to the reservation being cancelled. This prevents a stale cancellation from changing a seat that no longer belongs to that reservation.

### Reservation History

I keep the current seat ownership separate from the historical reservation-to-seat mapping.

When a reservation is cancelled, the current reservation reference on the seat is cleared so that another user can reserve it. The historical `ReservationSeat` mapping is kept, so the original reservation still records which seats belonged to it.

Repeated cancellation is handled safely and does not decrement the user's booking count or release the same seats multiple times.

### Why Temporary Holds Were Not Implemented

The assignment allows either owner cancellation or time-boxed holds for releasing inventory. I chose owner cancellation to keep the implementation focused on the main concurrency and reservation requirements.

If a checkout/payment hold were needed later, I would add a `HELD` state, an expiration timestamp and an expiry process. The expiry logic would still have to verify current seat ownership before releasing anything, so an old expiry event could not release a seat belonging to a newer reservation.


----------------

## 6. Consistency vs Availability

For seat reservation I prefer rejecting a request over confirming a seat when the current database state cannot be verified.

PostgreSQL is the source of truth for reservations. If it is unavailable, the application does not try to continue booking from local memory or cached seat state.

The readiness probe includes database health, so the service becomes not-ready when the database cannot be reached.

```text
Cannot verify current seat state
            |
            v
Do not accept reservation traffic
            |
            v
Avoid the risk of overselling
```

This is an intentional trade-off because temporarily rejecting reservations is safer than confirming the same seat for multiple users.

### Availability Under Load

The service uses different mechanisms for different responsibilities:

```text
Database transactions / locks / constraints
-> reservation correctness

Admission control
-> overload protection

Readiness
-> whether the instance should receive traffic

Metrics and structured logs
-> monitoring and troubleshooting
```

Admission control limits how many reservation requests enter the transactional path at once. Since it happens before a transaction starts, waiting requests do not consume database connections.

### ==> During Horizontal Scaling

The admission semaphore is local to an application instance.

If the application is horizontally scaled, every instance would apply its own admission limit. PostgreSQL would still protect the shared reservation data, but the combined concurrency from all application instances would need to be considered when sizing the database connection pool and database capacity.


----------------

## 7. Observability - What Would Page Me at 2 AM?

The service exposes health probes, Prometheus metrics and structured JSON logs with request IDs.

I look at observability in two parts: business behavior and operational failures. For example, many users getting `seat_taken` during a popular booking event can be completely normal, while a sudden increase in HTTP 5xx responses is something that needs investigation.

### Health

The application exposes:

```text
/actuator/health/liveness
/actuator/health/readiness
```

Liveness tells whether the application itself is running.

Readiness also checks database connectivity because the application cannot make a safe reservation decision without PostgreSQL. If the database is unavailable, readiness goes down instead of continuing to accept reservation traffic.

### Reservation Metrics

The custom Prometheus metrics include:

```text
reservation_confirmed_total

reservation_declined_total{reason="seat_taken"}

reservation_declined_total{reason="per_user_limit"}

reservation_declined_total{reason="idempotent_replay"}

seats_available{show_id="<show-id>"}
```

These metrics help track successful reservations, expected declines, retries and available inventory.

A high `seat_taken` count by itself is not necessarily an incident. It may simply mean many users are competing for the same limited inventory.

### Structured Logs & Request IDs

Logs are emitted as structured JSON.

A client can provide:

```text
X-Request-ID
```

If it is not provided, the application generates a request ID. The same ID is added to the response and logging context, which makes it easier to trace a particular request in the logs.

Expected seat conflicts are not logged at a high severity because a hot-seat event can otherwise generate a large amount of unnecessary logging.

### Now, What Would Page Me at 2 AM?

The main conditions I would consider urgent are:

- Sustained increase in HTTP 5xx responses
- Readiness remaining DOWN
- Database connectivity failures
- HikariCP connection-pool exhaustion or connection timeouts
- Sustained increase in request latency/timeouts
- Inventory reconciliation/invariant failure

The inventory invariant is:

```text
available + held + confirmed = total
```

An invariant failure is especially serious because it can indicate a reservation correctness or data-integrity problem.

On the other hand, expected business conflicts such as `seat_taken` and `per_user_limit` should be monitored, but I would not page someone only because those counters are high.


----------------

## 8. Load Testing & Findings

I used a k6 hot-seat test to exercise the reservation API under concurrent traffic.

The final test configuration was:

```text
20,000 reservation attempts
100 concurrent virtual users
1 hot seat
Unique user per attempt
Unique idempotency key per attempt
```

After the load test, the script also fetches the show state and verifies that exactly one reservation owns the hot seat and that the inventory counts are still consistent.

### Initial Problem - Connection Pool Exhaustion

The first large external load test exposed a problem that was not visible in the smaller concurrency tests.

The seat locking itself was protecting the booking correctly, but requests were exhausting the HikariCP connection pool before they reached the seat-lock logic.

The reason was that a database connection is required to begin the transaction:

```text
Request
   |
   v
Transaction needs DB connection
   |
   v
Seat lock logic
```

So even though the seat lock was configured to fail fast, a request could already be waiting for a connection before it reached that lock.

This resulted in connection-acquisition failures and HTTP 5xx responses during the initial load test.

### Fix - Admission Before the Transaction

I added admission control before the transactional service:

```text
HTTP Request
     |
     v
Admission Control
     |
     v
Begin Transaction
     |
     v
Acquire DB Connection
     |
     v
Reservation Logic
```

Requests waiting at the admission layer do not consume database connections. This prevented the reservation burst from exhausting the whole pool and also left capacity for other application operations.

### Final Railway Load Test

The final external test against the Railway deployment produced:

```text
Reservation attempts : 20,000
Concurrent VUs       : 100

Confirmed            : 1
Seat-taken conflicts : 19,981

Client network errors: 18
    ---> (This is occurring ecause of network error, on Render this count is 7 ut its taking 
           around 12 min to process 20,000 loads, while on Railway - network_error count is 18
           and its taking very less time to process same load)
           
HTTP 5xx responses   : 0
Interrupted          : 0
```

The final inventory reconciliation passed and exactly one reservation owned the hot seat.


The test represents 20,000 reservation attempts executed using 100 concurrent virtual users. It does not mean that 20,000 network connections were open at exactly the same time.

### Learning From the Load Test

The load test helped separate two different problems:

```text
PostgreSQL locks + constraints
-> protect booking correctness

Admission control
-> protects application/database capacity

Health + metrics + logs
-> help detect and investigate failures
```

The main learning was that correct database locking alone does not guarantee that the service will remain available during a traffic burst. Connection-pool capacity also has to be protected.

## AI Usage Disclosure

AI-assisted development tool was used during this exercise as permitted/mentioned in assignment.

I mainly used AI for:

- Discussing concurrency scenarios and edge cases around seat reservation
- Discussion around PostgreSQL locking and `NOWAIT` behavior
- Getting help around fixing the issues observed during functional and load testing
- Identifying additional test scenarios for concurrency, idempotency and cancellation
- Getting guidance around deployment/configuration issues when required
- Took help for writing the more clean structure/syntax for .md files, like:
    - How to give proper headings, subheading, etc.
    - Text diagram
    - Code Snippet
    - Or highlight any text