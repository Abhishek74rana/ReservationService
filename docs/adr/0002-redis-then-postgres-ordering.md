# ADR 0002 — Take from Redis before writing to Postgres

- **Status:** Accepted
- **Date:** 2026-09-17
- **Deciders:** Abhishek Rana

## Context

The hold path mutates two systems with no shared transaction:

1. The Redis available-count (ADR 0001).
2. The `reservation` row in PostgreSQL.

A crash, a timeout, or a pod eviction between the two leaves them inconsistent. The
question is not *whether* to have an inconsistency window — it is *which*
inconsistency to have, because there is no ordering that eliminates both.

## Decision

**Take from Redis first; write the reservation to Postgres second.**

If the process dies between the two steps, the result is a *leaked hold*: inventory
has been decremented in Redis, but no reservation row exists to confirm, cancel, or
expire it.

## Consequences

The two possible failure modes are not equivalent:

| Failure | What it means | Who resolves it |
|---|---|---|
| **Leaked hold** (chosen) | One unit is unavailable despite nobody owning it | The TTL sweeper, automatically, within `hold-ttl` (15 min) |
| **Oversell** (rejected) | Two customers hold the same unit; one must be told no after paying | A human — support, refund, apology |

A leaked hold is *self-healing*: `HoldExpiryScheduler` returns any hold whose
`held_until` has passed, and the unit re-enters the pool with no intervention. An
oversell requires a person, costs money, and damages trust.

When forced to choose which failure to have, choose the one that repairs itself.

**Cost accepted:** temporary false unavailability — inventory shows one fewer unit
than it truly has, for at most `hold-ttl`. This is invisible at normal capacity but
would matter for a genuinely scarce, high-demand drop. If that tradeoff ever needs to
flip, the alternative is to write the reservation row first in a `PENDING` state and
have a reconciler confirm it against Redis — which converts a leaked hold into a
leaked reservation requiring cleanup, and reintroduces the oversell risk in the
window before Redis is decremented.

## Related

- ADR 0001 — the atomic decrement this ordering depends on.
- ADR 0003 — reconciliation, which bounds how long Redis may diverge from Postgres.
