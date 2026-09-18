# ADR 0003 — Transactional outbox for event publication

- **Status:** Accepted
- **Date:** 2026-09-17
- **Deciders:** Abhishek Rana

## Context

Every state change to a reservation must be published to Kafka so downstream services
(notifications, analytics, availability projections) can react. The state change lives
in PostgreSQL; the event lives in Kafka. These are two systems with no shared
transaction, and both naive orderings are broken:

- **Publish, then commit.** If the transaction rolls back after the broker has
  acknowledged, we have announced a booking that does not exist. Downstream services
  act on a phantom.
- **Commit, then publish.** If the process dies between the commit and the send, the
  state change is durable but its event is lost silently. No error, no retry — the
  event simply never existed.

## Decision

Write the event to an `outbox_event` row **in the same transaction as the state
change**, and drain that table to Kafka from a background publisher.

```sql
CREATE TABLE outbox_event (
    id           UUID PRIMARY KEY,
    aggregate_id VARCHAR(100) NOT NULL,
    event_type   VARCHAR(60)  NOT NULL,
    payload      TEXT         NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL,
    published_at TIMESTAMPTZ,
    attempts     INTEGER      NOT NULL DEFAULT 0
);
```

The row and the state change commit together or fail together — the atomicity we
needed is provided by the transaction we already had.

## Consequences

**Positive**
- No phantom events: an event cannot exist for a rolled-back transaction.
- No lost events: a committed state change always has a durable outbox row to retry
  from.
- Publication is decoupled from the request path — the hold request does not wait on
  a broker round trip.

**Negative / accepted risks**
- **At-least-once delivery.** The publisher marks a row published after the broker
  acknowledges. If the acknowledgement arrives but the row update fails, the event is
  redelivered. Consumers must therefore be idempotent; dedup on the `eventId` in the
  payload is the contract (see ADR 0005 and the consumer example).
- **Added latency between commit and publication** — bounded by `poll-interval-ms`
  (1 s). Acceptable for downstream consumers here; would not be for a synchronous
  read-your-writes requirement.
- **A table that grows without bound** unless pruned. A production deployment needs a
  retention job deleting published rows older than a window.

## Alternatives considered

- **Direct `KafkaTemplate.send()` from the request path.** Rejected: neither ordering
  is safe, per the context above.
- **Kafka transactions (`@Transactional` Kafka + `transactional.id`).** Provides
  exactly-once semantics within Kafka, but does not make the *database* write and the
  *broker* write atomic — the original problem survives, with more moving parts. It also
  couples the request path to broker availability.
- **Change Data Capture (Debezium on the WAL).** A legitimate production choice that
  removes the publisher entirely, at the cost of operating Kafka Connect and a schema
  registry. Not justified at this service's size; noted as the migration path if
  outbox-table write amplification ever becomes the bottleneck.

## Related

- ADR 0002 — why the Redis write precedes the Postgres write.
- ADR 0005 — the consumer-side contract this decision imposes.
