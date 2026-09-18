# ADR 0001 — Redis Lua counter over database row locking

- **Status:** Accepted
- **Date:** 2026-09-17
- **Deciders:** Abhishek Rana

## Context

Inventory decrement has a check-then-act race. Two concurrent requests for the last
available unit both read `available = 1`, both pass the comparison, and both write —
selling one unit twice. Under concurrent load on a popular item this is the normal
case, not an edge case.

The conventional fix is `SELECT ... FOR UPDATE` on the inventory row, serialising
all bookings for that item behind one lock. This is correct but has two costs at
scale:

1. Every booking for a hot item queues behind a single row lock, so throughput on
   that item is capped by lock hold time.
2. Each waiting request holds a database connection for the duration, so the
   Hikari pool (20 connections by default here) becomes the bottleneck long before
   PostgreSQL itself does.

## Decision

Hold the hot available-count in Redis and mutate it with a Lua script executed via
`EVAL`. Redis executes a script as a single atomic unit, so no other client can
interleave between the read and the write — the race is closed without taking any
lock.

```lua
local available = tonumber(redis.call('GET', KEYS[1]))
if available == nil then return -1 end
local requested = tonumber(ARGV[1])
if available < requested then return -2 end
return redis.call('DECRBY', KEYS[1], requested)
```

PostgreSQL remains the system of record for reservations. Redis holds only a derived
count that can be rebuilt from the `reservation` table.

## Consequences

**Positive**
- No lock contention on hot items; throughput is bounded by Redis, not by row locks.
- No database connection is held across the critical section.
- The counter can be scaled independently (Redis Cluster, later).

**Negative / accepted risks**
- Redis becomes a dependency of the write path. If Redis is unavailable, holds fail —
  but availability, not correctness, is what is lost, because the reservation table
  remains authoritative.
- A second store must be kept consistent with the first. Any divergence is a bug we
  must detect rather than assume away; this is why reconciliation exists, and why
  drift is treated as an alertable condition.
- The operation is not transactional across Redis and Postgres. The ordering
  decision that follows from this is recorded separately in ADR 0002.

## Alternatives considered

- **`SELECT ... FOR UPDATE` on the inventory row.** Rejected for the throughput and
  connection-pool reasons above. Still the correct choice for low-contention
  operations — see ADR 0004 on optimistic locking for confirmation.
- **Single-row atomic `UPDATE ... SET available = available - :qty WHERE available >= :qty`.**
  Correct and lock-free at the statement level, but still a write to the primary
  database per booking, and still takes a row lock for the duration of the statement.
  It leaves the read path (availability checks dominate booking traffic) without a
  cache, so it worsens p99 read latency.
- **Application-level mutex / distributed lock (Redlock).** Adds a distributed-lock
  dependency and its failure modes to solve a problem Redis scripting already solves
  atomically in one round trip.
