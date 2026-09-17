package com.abhishekrana.reservation.service;

import com.abhishekrana.reservation.config.ReservationProperties;
import com.abhishekrana.reservation.domain.OutboxEvent;
import com.abhishekrana.reservation.domain.Reservation;
import com.abhishekrana.reservation.exception.ReservationNotFoundException;
import com.abhishekrana.reservation.repo.OutboxEventRepository;
import com.abhishekrana.reservation.repo.ReservationRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ReservationService {

    private static final Logger log = LoggerFactory.getLogger(ReservationService.class);

    private final ReservationRepository reservations;
    private final OutboxEventRepository outbox;
    private final InventoryService inventory;
    private final ReservationProperties props;
    private final ObjectMapper objectMapper;

    public ReservationService(ReservationRepository reservations,
                              OutboxEventRepository outbox,
                              InventoryService inventory,
                              ReservationProperties props,
                              ObjectMapper objectMapper) {
        this.reservations = reservations;
        this.outbox = outbox;
        this.inventory = inventory;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    /**
     * Holds inventory and records the reservation.
     *
     * Ordering matters here. We take from Redis first and write to Postgres
     * second, so a crash between the two leaks a hold rather than overselling.
     * A leaked hold is self-healing — the expiry sweeper returns it once the TTL
     * passes — whereas an oversell has to be resolved by a human.
     */
    @Transactional
    public Reservation hold(String idempotencyKey, String inventoryId, int quantity) {
        var existing = reservations.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            log.debug("Idempotent replay for key {}", idempotencyKey);
            return existing.get();
        }

        long remaining = inventory.take(inventoryId, quantity);

        Instant heldUntil = Instant.now().plus(props.getHoldTtl());
        Reservation reservation = Reservation.hold(idempotencyKey, inventoryId, quantity, heldUntil);

        try {
            reservations.saveAndFlush(reservation);
        } catch (DataIntegrityViolationException e) {
            // Two concurrent requests carried the same idempotency key and both
            // got past the read above. The unique constraint is the real
            // arbiter; give back what this thread took and return the winner.
            inventory.release(inventoryId, quantity);
            return reservations.findByIdempotencyKey(idempotencyKey)
                .orElseThrow(() -> e);
        }

        recordEvent(reservation, "reservation.held", Map.of(
            "remainingInventory", remaining,
            "heldUntil", heldUntil.toString()));

        return reservation;
    }

    @Transactional
    public Reservation confirm(UUID reservationId) {
        Reservation reservation = reservations.findById(reservationId)
            .orElseThrow(() -> new ReservationNotFoundException(reservationId));

        reservation.confirm();
        recordEvent(reservation, "reservation.confirmed", Map.of());
        return reservation;
    }

    @Transactional
    public Reservation cancel(UUID reservationId) {
        Reservation reservation = reservations.findById(reservationId)
            .orElseThrow(() -> new ReservationNotFoundException(reservationId));

        reservation.cancel();
        inventory.release(reservation.getInventoryId(), reservation.getQuantity());
        recordEvent(reservation, "reservation.cancelled", Map.of());
        return reservation;
    }

    @Transactional(readOnly = true)
    public Reservation get(UUID reservationId) {
        return reservations.findById(reservationId)
            .orElseThrow(() -> new ReservationNotFoundException(reservationId));
    }

    /**
     * Returns inventory for holds whose TTL has passed.
     *
     * Run by every instance on a short interval; SKIP LOCKED in the query means
     * instances divide the work instead of contending for the same rows.
     */
    @Transactional
    public int expireStaleHolds() {
        List<Reservation> stale = reservations.lockExpiredHolds(
            Instant.now(), props.getExpiry().getBatchSize());

        for (Reservation reservation : stale) {
            reservation.expire();
            inventory.release(reservation.getInventoryId(), reservation.getQuantity());
            recordEvent(reservation, "reservation.expired", Map.of());
        }

        if (!stale.isEmpty()) {
            log.info("Expired {} stale holds", stale.size());
        }
        return stale.size();
    }

    private void recordEvent(Reservation reservation, String eventType, Map<String, Object> extra) {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("eventId", UUID.randomUUID().toString());
        payload.put("eventType", eventType);
        payload.put("reservationId", reservation.getId().toString());
        payload.put("inventoryId", reservation.getInventoryId());
        payload.put("quantity", reservation.getQuantity());
        payload.put("status", reservation.getStatus().name());
        payload.put("occurredAt", Instant.now().toString());
        payload.putAll(extra);

        try {
            outbox.save(OutboxEvent.of(
                reservation.getId().toString(), eventType, objectMapper.writeValueAsString(payload)));
        } catch (JsonProcessingException e) {
            // Serialising a map of strings and numbers cannot realistically fail;
            // if it does, failing the transaction is correct — we must not commit
            // a state change whose event we cannot record.
            throw new IllegalStateException("Failed to serialise outbox payload", e);
        }
    }
}
