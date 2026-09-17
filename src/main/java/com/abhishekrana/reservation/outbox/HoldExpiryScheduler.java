package com.abhishekrana.reservation.outbox;

import com.abhishekrana.reservation.service.ReservationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class HoldExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(HoldExpiryScheduler.class);

    private final ReservationService reservations;

    public HoldExpiryScheduler(ReservationService reservations) {
        this.reservations = reservations;
    }

    @Scheduled(fixedDelayString = "${reservation.expiry.poll-interval-ms:5000}")
    public void sweep() {
        try {
            reservations.expireStaleHolds();
        } catch (Exception e) {
            // Never let a scheduled task die on an exception — Spring stops
            // rescheduling a task whose method throws.
            log.error("Hold expiry sweep failed", e);
        }
    }
}
