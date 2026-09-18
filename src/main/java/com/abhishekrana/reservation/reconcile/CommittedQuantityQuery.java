package com.abhishekrana.reservation.reconcile;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Authoritative unit counts, computed in the database rather than in Java.
 *
 * <p>Counting in SQL keeps the reconciliation job's memory flat regardless of how
 * many reservations exist for an item — summing in the application would mean
 * loading every row, which is exactly the thing that breaks the job once the table
 * grows. The composite index added in V2 makes this a covering index-only scan.
 */
@Component
public class CommittedQuantityQuery {

    @PersistenceContext
    private EntityManager em;

    /**
     * Units currently committed for an item — the sum of quantities across every
     * reservation in a holding status (HELD or CONFIRMED).
     *
     * <p>CANCELLED and EXPIRED reservations have already returned their units to
     * the pool, so they are excluded here. If they were included, every completed
     * cancellation would register as drift and the metric would be useless.
     */
    @Transactional(readOnly = true)
    public long committedQuantity(String inventoryId) {
        Long committed = em.createQuery(
                "SELECT COALESCE(SUM(r.quantity), 0) FROM Reservation r " +
                "WHERE r.inventoryId = :inventoryId " +
                "AND r.status IN (com.abhishekrana.reservation.domain.ReservationStatus.HELD, " +
                "com.abhishekrana.reservation.domain.ReservationStatus.CONFIRMED)",
                Long.class)
            .setParameter("inventoryId", inventoryId)
            .getSingleResult();

        return committed == null ? 0L : committed;
    }
}
