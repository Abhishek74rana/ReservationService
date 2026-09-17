# reservation-service

An event-driven inventory reservation service: hold stock, confirm or cancel it, and
publish the resulting state changes reliably. The problem it solves is the one that
sits under every booking, ticketing, and checkout system — **never sell the same unit
twice, even when hundreds of requests arrive for the last one at the same instant.**

Java 17 · Spring Boot 3 · PostgreSQL · Redis · Kafka

---

## The problem

Naive inventory decrement looks like this:

```java
int available = repo.getAvailable(roomId);   // reads 1
if (available >= qty) {                      // both threads pass
    repo.decrement(roomId, qty);             // both write 0
}
```

Two threads read the same value before either writes. Both sell the last room.
Under load this is not a rare race — it is the normal case.

The usual fix, `SELECT ... FOR UPDATE`, is correct but serialises every booking for
a popular item behind a single row lock, and holds a database connection for the
duration. At a few hundred concurrent requests the connection pool is the bottleneck
long before the database is.

## The approach

Hot inventory counters live in **Redis**, decremented by a **Lua script**. Redis runs
a script as a single unit, so the read and the write cannot be interleaved by another
client — the check-then-act race is closed without any lock being taken.

```lua
local available = tonumber(redis.call('GET', KEYS[1]))
if available < requested then return -2 end
return redis.call('DECRBY', KEYS[1], requested)
```

PostgreSQL remains the system of record for reservations. Redis holds only a derived
count, rebuildable from the reservation table, so losing Redis costs availability but
never correctness.

## Architecture

```
                  ┌──────────────────────┐
  POST /reservations ──▶ ReservationService │
                  └──────────┬───────────┘
                             │
              ┌──────────────┴──────────────┐
              ▼                             ▼
      ┌───────────────┐            ┌─────────────────┐
      │     Redis     │            │   PostgreSQL    │
      │  EVAL Lua     │            │  reservation    │
      │  atomic DECR  │            │  outbox_event   │
      └───────────────┘            └────────┬────────┘
                                            │ same transaction
                                            ▼
                                   ┌──────────────────┐
                                   │ OutboxPublisher  │──▶ Kafka
                                   │  (SKIP LOCKED)   │    reservation-events
                                   └──────────────────┘
```

A reservation moves `HELD → CONFIRMED | CANCELLED | EXPIRED`. Holds carry a TTL; a
sweeper returns inventory for any hold that was never confirmed.

## Design decisions

**Redis take happens before the Postgres write.** A crash between the two leaks a
hold rather than overselling. A leaked hold heals itself when the TTL sweeper runs;
an oversell requires a human, an apology, and usually a refund. When you must choose
which failure to have, choose the self-healing one.

**Events go through a transactional outbox, not a direct Kafka send.** Writing to the
database and publishing to a broker are two systems with no shared transaction. Send
first and a rolled-back transaction has already announced a booking that does not
exist; send after commit and a crash in between loses the event silently. The outbox
row is written in the same transaction as the state change, and a background publisher
drains it — so the event and the state change commit or fail together.

**Delivery is at-least-once, so consumers deduplicate.** Exactly-once across a
database and a broker needs distributed transactions; at-least-once plus an `eventId`
in the payload gets the same practical guarantee for a fraction of the complexity.
Kafka records are keyed on reservation id, which pins one reservation's events to one
partition and preserves their order.

**Both background jobs use `FOR UPDATE SKIP LOCKED`.** Every instance runs the sweeper
and the publisher; SKIP LOCKED makes them divide the rows instead of contending for
them. No leader election, no distributed lock, no single point of failure.

**Idempotency keys on the write path.** Clients retry — on timeout, on 502, on a user
double-tapping a button. A unique constraint on the key makes the retry return the
original reservation instead of holding inventory twice, and the constraint (not the
read that precedes it) is what actually arbitrates a race between two concurrent
retries.

**Optimistic locking via `@Version` on the reservation.** Confirmation is a low-
contention operation on a single reservation; pessimistic locking would cost more
than the rare retry.

## Running it

```bash
docker compose up -d          # postgres, redis, kafka
./mvnw spring-boot:run        # Flyway migrates on startup
```

Swagger UI: http://localhost:8080/swagger-ui.html

```bash
# register capacity
curl -X POST localhost:8080/api/v1/inventory \
  -H 'Content-Type: application/json' \
  -d '{"inventoryId":"room-101","capacity":5}'

# hold two units (retry-safe)
curl -X POST localhost:8080/api/v1/reservations \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: 9f1c-demo-001' \
  -d '{"inventoryId":"room-101","quantity":2}'

# confirm
curl -X POST localhost:8080/api/v1/reservations/{id}/confirm
```

## Tests

```bash
./mvnw verify
```

Integration tests run against real Postgres and Redis via Testcontainers, because the
behaviour under test *is* the behaviour of those engines — Lua atomicity, SKIP LOCKED,
unique constraint arbitration. A mock would pass while production is broken.

The load-bearing test fires 300 threads at 50 units of capacity behind a
`CountDownLatch` start gate and asserts exactly 50 succeed, 250 are rejected, and the
remaining count is zero.

## What this deliberately does not do

- **No auth.** Out of scope; assume an API gateway terminates it.
- **No multi-region.** The Redis counter is a single-region construct. Going
  multi-region means either partitioning inventory by region or accepting a consensus
  round trip per booking.
- **No reconciliation job.** Production would want a periodic job that rebuilds the
  Redis count from the reservation table and alerts on drift.

## Roadmap

- [ ] Reconciliation job for Redis/Postgres drift
- [ ] Redis Cluster with hash-tagged keys so a script's keys land in one slot
- [ ] Consumer-side deduplication example service
- [ ] Rate limiting per client on the hold endpoint
