package com.abhishekrana.reservation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Reconciliation settings, nested under {@code reservation.reconcile}.
 *
 * <p>Merged into {@link ReservationProperties} as a nested object so the
 * reconciliation job's configuration lives with the rest of the service's,
 * rather than in a second properties class.
 */
@ConfigurationProperties(prefix = "reservation.reconcile")
public class ReconcileProperties {

    /** Whether the periodic drift check runs at all. */
    private boolean enabled = true;

    /**
     * Whether a detected drift is corrected. Off by default: detection alerts a
     * human, repair hides the bug that caused the divergence.
     */
    private boolean repair = false;

    private long pollIntervalMs = 60_000L;

    private long initialDelayMs = 30_000L;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isRepair() {
        return repair;
    }

    public void setRepair(boolean repair) {
        this.repair = repair;
    }

    public long getPollIntervalMs() {
        return pollIntervalMs;
    }

    public void setPollIntervalMs(long pollIntervalMs) {
        this.pollIntervalMs = pollIntervalMs;
    }

    public long getInitialDelayMs() {
        return initialDelayMs;
    }

    public void setInitialDelayMs(long initialDelayMs) {
        this.initialDelayMs = initialDelayMs;
    }
}
