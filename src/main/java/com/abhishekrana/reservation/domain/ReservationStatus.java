package com.abhishekrana.reservation.domain;

public enum ReservationStatus {
    /** Inventory is held in Redis but payment has not completed. Expires automatically. */
    HELD,
    /** Payment confirmed; the hold has been converted into a permanent booking. */
    CONFIRMED,
    /** Hold expired before confirmation; inventory was returned. */
    EXPIRED,
    /** Explicitly cancelled by the caller; inventory was returned. */
    CANCELLED
}
