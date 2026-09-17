package com.abhishekrana.reservation;

import com.abhishekrana.reservation.exception.InsufficientInventoryException;
import com.abhishekrana.reservation.service.InventoryService;
import com.abhishekrana.reservation.service.ReservationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The test that justifies the whole design: many threads racing for the last
 * few units must never oversell.
 */
class ReservationConcurrencyIT extends AbstractIntegrationTest {

    @Autowired ReservationService reservations;
    @Autowired InventoryService inventory;

    @Test
    void concurrentHoldsNeverOversellInventory() throws Exception {
        String inventoryId = "room-" + UUID.randomUUID();
        int capacity = 50;
        int contenders = 300;

        inventory.register(inventoryId, capacity);

        var startGate = new CountDownLatch(1);
        var finished = new CountDownLatch(contenders);
        var succeeded = new AtomicInteger();
        var rejected = new AtomicInteger();

        ExecutorService pool = Executors.newFixedThreadPool(32);
        try {
            for (int i = 0; i < contenders; i++) {
                pool.submit(() -> {
                    try {
                        startGate.await();   // release all threads at once
                        reservations.hold(UUID.randomUUID().toString(), inventoryId, 1);
                        succeeded.incrementAndGet();
                    } catch (InsufficientInventoryException e) {
                        rejected.incrementAndGet();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        finished.countDown();
                    }
                });
            }

            startGate.countDown();
            assertThat(finished.await(60, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }

        assertThat(succeeded.get()).isEqualTo(capacity);
        assertThat(rejected.get()).isEqualTo(contenders - capacity);
        assertThat(inventory.available(inventoryId)).isZero();
    }

    @Test
    void repeatedIdempotencyKeyHoldsInventoryOnlyOnce() {
        String inventoryId = "room-" + UUID.randomUUID();
        inventory.register(inventoryId, 10);

        String key = UUID.randomUUID().toString();
        var first = reservations.hold(key, inventoryId, 3);
        var second = reservations.hold(key, inventoryId, 3);

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(inventory.available(inventoryId)).isEqualTo(7);
    }

    @Test
    void cancellingReturnsInventory() {
        String inventoryId = "room-" + UUID.randomUUID();
        inventory.register(inventoryId, 5);

        var reservation = reservations.hold(UUID.randomUUID().toString(), inventoryId, 2);
        assertThat(inventory.available(inventoryId)).isEqualTo(3);

        reservations.cancel(reservation.getId());
        assertThat(inventory.available(inventoryId)).isEqualTo(5);
    }
}
