# ADR 0005 — At-least-once delivery with consumer-side deduplication

- **Status:** Accepted
- **Date:** 2026-09-17
- **Deciders:** Abhishek Rana

## Context

The outbox publisher (ADR 0003) delivers at-least-once. A broker acknowledgement that
arrives after the outbox row update fails causes a redelivery on the next pass. Two
deliveries of `reservation.confirmed` are also not equivalent to two pieces of news: a
redelivered event must not send a second confirmation email or decrement a projection
twice.

Three delivery guarantees are available:

1. **At-most-once** — never redeliver, risk losing events.
2. **Exactly-once across database and broker** — requires distributed transactions
   (XA) or Kafka transactions spanning an external store, neither of which Kafka
   provides.
3. **At-least-once + idempotent consumers** — redeliver freely; make the effect
   idempotent.

## Decision

At-least-once delivery, with the consumer responsible for deduplication.

Every payload carries a unique `eventId` generated at write time:

```json
{
  "eventId": "9f1c...",
  "eventType": "reservation.held",
  "reservationId": "...",
  "inventoryId": "room-101",
  "quantity": 2,
  "status": "HELD",
  "occurredAt": "2026-09-17T16:40:00Z"
}
```

A consumer records processed `eventId`s and skips a payload it has already handled.
Kafka records are keyed on `aggregateId` (the reservation id), which pins all events
for one reservation to a single partition and preserves their per-reservation order —
so the dedup table only needs to answer "have I seen this event", not "is this event
older than the last one I applied".

## Consequences

**Positive**
- No distributed transaction, no XA, no two-phase commit.
- Consumers can crash, retry, and rebalance without losing or duplicating effects.
- The guarantee is explicit and testable, rather than assumed.

**Negative / accepted risks**
- **Every consumer must implement dedup.** This is a real cost — six consumers mean
  six dedup implementations. An event-sourcing or Kafka-Streams-based consumer layer
  with built-in processing guarantees would hold this better than a hand-rolled table.
- The dedup store grows; it needs a retention window at least as long as the maximum
  redelivery delay.
- **Non-idempotent side effects cannot be made safe this way.** Sending an email is
  only deduplicable while the dedup record is authoritative — a message sent and then
  crashed before recording would be sent twice. Truly non-idempotent effects need
  their own outbox on the consumer side, which is the same pattern one hop further
  along.

## Alternatives considered

- **Kafka transactions on the consumer side** — solves ordering and duplicate reads
  within Kafka, but not the external side effect (the email, the projection write)
  that is the actual source of harm.
- **Idempotent-by-construction effects** — the ideal, and used where possible (an
  upsert on the projection is naturally idempotent). Not always available; dedup is
  the general fallback.
