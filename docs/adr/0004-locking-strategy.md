# ADR 0004 — Per-operation locking strategy

- **Status:** Accepted
- **Date:** 2026-09-17
- **Deciders:** Abhishek Rana

## Context

The service performs three kinds of write, each with a different contention profile.
Applying one locking strategy uniformly would either over-lock the hot path or
under-protect the correctness-critical one.

| Operation | Contention | Cost of a conflict |
|---|---|---|
| Hold | High (many concurrent requests for the same item) | A retry |
| Confirm | Low (single reservation, single client) | A retry |
| Expiry sweep | Medium, across instances | Duplicate work |

## Decision

Choose the strategy per operation:

**Hold — no application lock; a unique constraint arbitrates.** The idempotency key
carries a `UNIQUE` constraint, and the database — not a read followed by a check — is
what resolves two concurrent retries carrying the same key. The pre-read
(`findByIdempotencyKey`) is an optimisation; the constraint-violation path is the
correctness guarantee:

```java
try {
    reservations.saveAndFlush(reservation);
} catch (DataIntegrityViolationException e) {
    inventory.release(inventoryId, quantity);   // give back what this thread took
    return reservations.findByIdempotencyKey(idempotencyKey).orElseThrow(() -> e);
}
```

**Confirm / cancel — optimistic locking via `@Version`.** Contention on a single
reservation is low, so a rare version conflict and retry costs less than the
connection held by a pessimistic lock.

**Background jobs — `FOR UPDATE SKIP LOCKED`.** Every instance runs the sweeper and
the publisher. `SKIP LOCKED` makes them *divide* the rows rather than contend for
them, so no leader election, no distributed lock, and no single point of failure is
required.

```sql
SELECT * FROM reservation
WHERE status = 'HELD' AND held_until < :now
ORDER BY held_until
LIMIT :batchSize
FOR UPDATE SKIP LOCKED
```

## Consequences

**Positive**
- Each path pays only for the coordination it actually needs.
- The hot path (hold) takes no long-held lock at all.
- Background work scales by adding instances, with no coordination service.

**Negative / accepted risks**
- Three strategies is more to understand than one. This ADR exists so the reasoning is
  recorded rather than re-derived.
- `SKIP LOCKED` means a long-running batch can starve rows held by another instance;
  bounded batch sizes (`expiry.batch-size`, `outbox.batch-size`) keep the window small.

## Related

- ADR 0001 — the Redis-level atomicity that makes lock-free holds possible.
