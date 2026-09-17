package com.abhishekrana.reservation.exception;

public class InsufficientInventoryException extends RuntimeException {
    public InsufficientInventoryException(String inventoryId, int requested) {
        super("Insufficient inventory for " + inventoryId + ", requested " + requested);
    }
}
