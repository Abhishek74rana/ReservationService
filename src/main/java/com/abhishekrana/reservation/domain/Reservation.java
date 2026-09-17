package com.abhishekrana.reservation.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "reservation",
    uniqueConstraints = @UniqueConstraint(name = "uk_reservation_idempotency", columnNames = "idempotency_key")
)
public class Reservation {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * Client-supplied key that makes POST /reservations safe to retry. A retried
     * request with the same key returns the original reservation instead of
     * holding inventory twice.
     */
    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "inventory_id", nullable = false, updatable = false, length = 100)
    private String inventoryId;

    @Column(name = "quantity", nullable = false, updatable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReservationStatus status;

    @Column(name = "held_until", nullable = false)
    private Instant heldUntil;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Optimistic lock. Two threads confirming the same reservation concurrently
     * collide here rather than both emitting a CONFIRMED event.
     */
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected Reservation() {
        // required by JPA
    }

    public static Reservation hold(String idempotencyKey, String inventoryId, int quantity, Instant heldUntil) {
        Reservation r = new Reservation();
        Instant now = Instant.now();
        r.id = UUID.randomUUID();
        r.idempotencyKey = idempotencyKey;
        r.inventoryId = inventoryId;
        r.quantity = quantity;
        r.status = ReservationStatus.HELD;
        r.heldUntil = heldUntil;
        r.createdAt = now;
        r.updatedAt = now;
        return r;
    }

    public void confirm() {
        requireStatus(ReservationStatus.HELD, "confirm");
        this.status = ReservationStatus.CONFIRMED;
        this.updatedAt = Instant.now();
    }

    public void cancel() {
        requireStatus(ReservationStatus.HELD, "cancel");
        this.status = ReservationStatus.CANCELLED;
        this.updatedAt = Instant.now();
    }

    public void expire() {
        requireStatus(ReservationStatus.HELD, "expire");
        this.status = ReservationStatus.EXPIRED;
        this.updatedAt = Instant.now();
    }

    private void requireStatus(ReservationStatus expected, String action) {
        if (this.status != expected) {
            throw new IllegalStateException(
                "Cannot " + action + " reservation " + id + " in status " + status);
        }
    }

    public boolean isExpired(Instant now) {
        return status == ReservationStatus.HELD && now.isAfter(heldUntil);
    }

    public UUID getId() { return id; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getInventoryId() { return inventoryId; }
    public int getQuantity() { return quantity; }
    public ReservationStatus getStatus() { return status; }
    public Instant getHeldUntil() { return heldUntil; }
    public Instant getCreatedAt() { return createdAt; }
    public long getVersion() { return version; }
}
