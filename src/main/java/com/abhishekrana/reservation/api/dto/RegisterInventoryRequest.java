package com.abhishekrana.reservation.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record RegisterInventoryRequest(
    @NotBlank String inventoryId,
    @Min(0) int capacity
) {}
