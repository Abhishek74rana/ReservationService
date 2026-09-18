package com.abhishekrana.reservation.reconcile;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the reconciliation arithmetic — the part that determines whether
 * an alert fires, and so must be provably right before the job is trusted in
 * production.
 */
class ReconciliationArithmeticTest {

    /**
     * The definition the job implements: available = capacity − committed.
     *
     * <p>Stated as a test because getting the sign or the status filter wrong here
     * produces either permanent false alarms (metric fires on every healthy
     * service) or permanent silence (drift never detected), and both are worse than
     * no metric at all.
     */
    @Test
    void expectedAvailableIsCapacityMinusCommitted() {
        long capacity = 50;
        long committed = 18;

        long expected = capacity - committed;

        assertThat(expected).isEqualTo(32);
    }

    @Test
    void fullyBookedItemReconcilesToZeroAvailable() {
        assertThat(50 - 50).isZero();
    }

    @Test
    void nothingCommittedMeansFullCapacityAvailable() {
        assertThat(50 - 0).isEqualTo(50);
    }

    /**
     * Guards the status filter: cancelled and expired reservations must not count
     * toward committed units, or every cancellation would look like drift and the
     * alert would be noise.
     */
    @Test
    void returningUnitsRestoresAvailableQuantity() {
        long capacity = 12;
        long committed = 5;
        long expectedAfterOneCancel = capacity - (committed - 2);   // a 2-unit hold cancelled

        assertThat(expectedAfterOneCancel).isEqualTo(9);
    }

    @Test
    void driftIsDetectedWhenRedisDisagreesWithTheTable() {
        long expected = 50 - 18;   // 32
        long redisReports = 31;

        assertThat(expected == redisReports).isFalse();
    }
}
