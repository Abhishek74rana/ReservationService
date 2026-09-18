package com.abhishekrana.reservation.reconcile;

import com.abhishekrana.reservation.config.ReservationProperties;
import com.abhishekrana.reservation.repo.ReservationRepository;
import com.abhishekrana.reservation.service.InventoryService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Rebuilds the Redis available-count from the reservation table and reports drift.
 *
 * <p>The counter in Redis is a derived value (ADR 0001): the reservation table is
 * the system of record. Between them sit the failure modes recorded in ADR 0002 —
 * a crashed hold leaks inventory — and a bug in the Lua scripts would diverge them
 * silently. None of those self-report. This job is what turns "Redis and Postgres
 * disagree" from an assumption into a documented, alertable condition.
 *
 * <p>Two modes, because they answer different questions:
 *
 * <ul>
 *   <li><b>DETECT</b> (default) — compare, log, and emit a metric, but change
 *       nothing. Safe to run on every instance. This is what you alert on.</li>
 *   <li><b>REPAIR</b> — additionally overwrite the Redis counter with the
 *       authoritative value. Opt-in, because silently correcting a divergence
 *       hides the bug that caused it; the metric should fire and a human should
 *       understand it before automatic repair is trusted.</li>
 * </ul>
 *
 * <p>The authoritative count is computed the way the system defines it:
 *
 * <pre>
 *   available = capacity - SUM(quantity of reservations in a holding status)
 * </pre>
 *
 * <p>where a holding status is HELD or CONFIRMED — the two states in which units
 * are actually committed. CANCELLED and EXPIRED have already returned their units,
 * so they must not be subtracted, or every completed booking would look like drift.
 */
@Component
@ConditionalOnProperty(name = "reservation.reconcile.enabled", havingValue = "true", matchIfMissing = true)
public class InventoryReconciliationJob {

    private static final Logger log = LoggerFactory.getLogger(InventoryReconciliationJob.class);

    private final ReservationRepository reservations;
    private final InventoryService inventory;
    private final ReservationProperties props;
    private final Counter driftDetected;
    private final Counter driftRepaired;

    private volatile LastRun lastRun;

    public InventoryReconciliationJob(ReservationRepository reservations,
                                      InventoryService inventory,
                                      ReservationProperties props,
                                      MeterRegistry meters) {
        this.reservations = reservations;
        this.inventory = inventory;
        this.props = props;
        this.driftDetected = Counter.builder("inventory.reconcile.drift.detected")
            .description("Inventory items whose Redis count disagreed with the reservation table")
            .register(meters);
        this.driftRepaired = Counter.builder("inventory.reconcile.drift.repaired")
            .description("Inventory items whose Redis count was corrected by reconciliation")
            .register(meters);
    }

    @Scheduled(
        fixedDelayString = "${reservation.reconcile.poll-interval-ms:60000}",
        initialDelayString = "${reservation.reconcile.initial-delay-ms:30000}")
    public void reconcile() {
        List<String> inventoryIds = inventory.registeredInventoryIds();
        if (inventoryIds.isEmpty()) {
            return;
        }

        int drifted = 0;
        for (String inventoryId : inventoryIds) {
            if (reconcileOne(inventoryId)) {
                drifted++;
            }
        }

        lastRun = new LastRun(Instant.now(), inventoryIds.size(), drifted);

        if (drifted > 0) {
            log.warn("Reconciliation found drift on {} of {} inventory items",
                drifted, inventoryIds.size());
        } else {
            log.debug("Reconciliation clean across {} inventory items", inventoryIds.size());
        }
    }

    /**
     * @return true if the Redis count disagreed with the table
     */
    private boolean reconcileOne(String inventoryId) {
        Long capacity = inventory.registeredCapacity(inventoryId);
        if (capacity == null) {
            return false;   // item vanished between listing and lookup
        }

        long committed = reservations.sumCommittedQuantity(inventoryId);
        long expected = capacity - committed;
        Long actual = inventory.availableOrNull(inventoryId);

        if (actual == null) {
            // Counter missing entirely — the item was never registered, or the key
            // expired. Not drift; registering is the caller's job.
            return false;
        }

        if (expected == actual) {
            return false;
        }

        driftDetected.increment();
        log.warn("Inventory drift on {}: redis={} expected={} (capacity={} committed={})",
            inventoryId, actual, expected, capacity, committed);

        if (props.getReconcile().isRepair()) {
            inventory.overwriteAvailable(inventoryId, expected);
            driftRepaired.increment();
            log.info("Repaired {}: set redis count to {}", inventoryId, expected);
        }

        return true;
    }

    LastRun lastRun() {
        return lastRun;
    }

    /** Outcome of the most recent pass, for the health indicator. */
    record LastRun(Instant at, int itemsChecked, int drifted) {
    }
}
