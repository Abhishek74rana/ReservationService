package com.abhishekrana.reservation.api;

import com.abhishekrana.reservation.api.dto.ErrorResponse;
import com.abhishekrana.reservation.exception.InsufficientInventoryException;
import com.abhishekrana.reservation.exception.ReservationNotFoundException;
import com.abhishekrana.reservation.exception.UnknownInventoryException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InsufficientInventoryException.class)
    public ResponseEntity<ErrorResponse> handleSoldOut(InsufficientInventoryException e) {
        // 409 rather than 400: the request is well formed, the resource state is
        // simply incompatible with it right now.
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ErrorResponse.of("INSUFFICIENT_INVENTORY", e.getMessage()));
    }

    @ExceptionHandler({ReservationNotFoundException.class, UnknownInventoryException.class})
    public ResponseEntity<ErrorResponse> handleNotFound(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse.of("NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ErrorResponse.of("INVALID_STATE", e.getMessage()));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleConcurrentUpdate(ObjectOptimisticLockingFailureException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ErrorResponse.of("CONCURRENT_MODIFICATION",
                "Reservation was modified concurrently; retry the request"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
            .map(f -> f.getField() + ": " + f.getDefaultMessage())
            .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(ErrorResponse.of("VALIDATION_FAILED", detail));
    }
}
