package com.abhishekrana.reservation.exception;

public class UnknownInventoryException extends RuntimeException {
    public UnknownInventoryException(String inventoryId) {
        super("No inventory registered for " + inventoryId);
    }
}
