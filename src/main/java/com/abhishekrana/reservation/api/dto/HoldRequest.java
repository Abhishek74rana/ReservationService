package com.abhishekrana.reservation.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record HoldRequest(
    @NotBlank(message = "inventoryId is required")
    String inventoryId,

    @Min(value = 1, message = "quantity must be at least 1")
    @Max(value = 100, message = "quantity must not exceed 100")
    int quantity
) {}
