package com.abhishekrana.reservation.reconcile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Exposes the last reconciliation outcome on {@code /actuator/health}.
 *
 * <p>Drift is not a reason to take the instance out of service — the reservation
 * table is still authoritative and holds still work — so it is reported as a
 * non-fatal <em>DEGRADED</em> signal rather than DOWN. Readiness for a booking
 * service must not depend on a background job's last run, or a slow sweep would
 * pull healthy pods out of rotation and turn a cosmetic problem into an outage.
 */
@Component("inventoryReconciliation")
public class ReconciliationHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationHealthIndicator.class);

    private final InventoryReconciliationJob job;

    public ReconciliationHealthIndicator(InventoryReconciliationJob job) {
        this.job = job;
    }

    @Override
    public Health health() {
        InventoryReconciliationJob.LastRun last = job.lastRun();
        if (last == null) {
            return Health.unknown()
                .withDetail("status", "not yet run")
                .build();
        }

        Health.Builder builder = last.drifted() == 0
            ? Health.up()
            : Health.status("DEGRADED");

        return builder
            .withDetail("checkedAt", last.at().toString())
            .withDetail("itemsChecked", last.itemsChecked())
            .withDetail("itemsDrifted", last.drifted())
            .build();
    }
}
