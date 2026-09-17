package com.abhishekrana.reservation.repo;

import com.abhishekrana.reservation.domain.Reservation;
import com.abhishekrana.reservation.domain.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    Optional<Reservation> findByIdempotencyKey(String idempotencyKey);

    /**
     * Sweeps holds whose TTL has passed. SKIP LOCKED lets several application
     * instances run the sweeper concurrently without blocking each other or
     * processing the same row twice.
     */
    @Query(value = """
        SELECT * FROM reservation
        WHERE status = 'HELD' AND held_until < :now
        ORDER BY held_until
        LIMIT :limit
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<Reservation> lockExpiredHolds(@Param("now") Instant now, @Param("limit") int limit);

    long countByInventoryIdAndStatus(String inventoryId, ReservationStatus status);
}
