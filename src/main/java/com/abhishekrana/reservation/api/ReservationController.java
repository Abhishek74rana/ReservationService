package com.abhishekrana.reservation.api;

import com.abhishekrana.reservation.api.dto.HoldRequest;
import com.abhishekrana.reservation.api.dto.RegisterInventoryRequest;
import com.abhishekrana.reservation.api.dto.ReservationResponse;
import com.abhishekrana.reservation.service.InventoryService;
import com.abhishekrana.reservation.service.ReservationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Validated
public class ReservationController {

    private final ReservationService reservations;
    private final InventoryService inventory;

    public ReservationController(ReservationService reservations, InventoryService inventory) {
        this.reservations = reservations;
        this.inventory = inventory;
    }

    @PostMapping("/inventory")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void registerInventory(@Valid @RequestBody RegisterInventoryRequest request) {
        inventory.register(request.inventoryId(), request.capacity());
    }

    @GetMapping("/inventory/{inventoryId}")
    public Map<String, Object> availability(@PathVariable String inventoryId) {
        return Map.of("inventoryId", inventoryId, "available", inventory.available(inventoryId));
    }

    /**
     * Holds inventory. The Idempotency-Key header makes the call safe to retry:
     * a repeat with the same key returns the original reservation rather than
     * taking inventory twice.
     */
    @PostMapping("/reservations")
    public ResponseEntity<ReservationResponse> hold(
            @RequestHeader("Idempotency-Key") @NotBlank String idempotencyKey,
            @Valid @RequestBody HoldRequest request) {

        var reservation = reservations.hold(idempotencyKey, request.inventoryId(), request.quantity());
        return ResponseEntity.status(HttpStatus.CREATED).body(ReservationResponse.from(reservation));
    }

    @PostMapping("/reservations/{id}/confirm")
    public ReservationResponse confirm(@PathVariable UUID id) {
        return ReservationResponse.from(reservations.confirm(id));
    }

    @PostMapping("/reservations/{id}/cancel")
    public ReservationResponse cancel(@PathVariable UUID id) {
        return ReservationResponse.from(reservations.cancel(id));
    }

    @GetMapping("/reservations/{id}")
    public ReservationResponse get(@PathVariable UUID id) {
        return ReservationResponse.from(reservations.get(id));
    }
}
