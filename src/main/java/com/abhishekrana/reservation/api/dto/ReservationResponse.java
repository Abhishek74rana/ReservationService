package com.abhishekrana.reservation.api.dto;

import com.abhishekrana.reservation.domain.Reservation;

import java.time.Instant;
import java.util.UUID;

public record ReservationResponse(
    UUID id,
    String inventoryId,
    int quantity,
    String status,
    Instant heldUntil,
    Instant createdAt
) {
    public static ReservationResponse from(Reservation r) {
        return new ReservationResponse(
            r.getId(), r.getInventoryId(), r.getQuantity(),
            r.getStatus().name(), r.getHeldUntil(), r.getCreatedAt());
    }
}
