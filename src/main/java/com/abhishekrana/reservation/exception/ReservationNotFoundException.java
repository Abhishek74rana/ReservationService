package com.abhishekrana.reservation.exception;

import java.util.UUID;

public class ReservationNotFoundException extends RuntimeException {
    public ReservationNotFoundException(UUID id) {
        super("Reservation not found: " + id);
    }
}
