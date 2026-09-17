package com.abhishekrana.reservation.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Transactional outbox row.
 *
 * The event is written in the same database transaction as the state change it
 * describes, so we can never end up with a committed reservation whose event was
 * lost, or an event for a transaction that rolled back. A background publisher
 * drains this table into Kafka.
 */
@Entity
@Table(name = "outbox_event")
public class OutboxEvent {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "aggregate_id", nullable = false, length = 100)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 60)
    private String eventType;

    @Column(name = "payload", nullable = false, columnDefinition = "text")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    protected OutboxEvent() {
    }

    public static OutboxEvent of(String aggregateId, String eventType, String payload) {
        OutboxEvent e = new OutboxEvent();
        e.id = UUID.randomUUID();
        e.aggregateId = aggregateId;
        e.eventType = eventType;
        e.payload = payload;
        e.createdAt = Instant.now();
        e.attempts = 0;
        return e;
    }

    public void markPublished() {
        this.publishedAt = Instant.now();
    }

    public void markAttemptFailed() {
        this.attempts++;
    }

    public UUID getId() { return id; }
    public String getAggregateId() { return aggregateId; }
    public String getEventType() { return eventType; }
    public String getPayload() { return payload; }
    public Instant getPublishedAt() { return publishedAt; }
    public int getAttempts() { return attempts; }
}
