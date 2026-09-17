package com.abhishekrana.reservation.service;

import com.abhishekrana.reservation.exception.InsufficientInventoryException;
import com.abhishekrana.reservation.exception.UnknownInventoryException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Owns the hot counter for each inventory item.
 *
 * Reads and decrements happen in Redis rather than Postgres because the booking
 * flow is read-heavy and latency sensitive, and because a SELECT ... FOR UPDATE
 * on a single popular row serialises every concurrent booking behind one lock.
 * Postgres remains the source of truth for reservations; Redis holds only the
 * derived available count, which can be rebuilt from the reservation table.
 */
@Service
public class InventoryService {

    private static final long UNKNOWN_KEY = -1L;
    private static final long INSUFFICIENT = -2L;

    private final StringRedisTemplate redis;
    private final RedisScript<Long> reserveScript;
    private final RedisScript<Long> releaseScript;

    public InventoryService(StringRedisTemplate redis,
                            RedisScript<Long> reserveScript,
                            RedisScript<Long> releaseScript) {
        this.redis = redis;
        this.reserveScript = reserveScript;
        this.releaseScript = releaseScript;
    }

    /** Registers (or resets) capacity for an item. */
    public void register(String inventoryId, int capacity) {
        redis.opsForValue().set(capacityKey(inventoryId), String.valueOf(capacity));
        redis.opsForValue().set(availableKey(inventoryId), String.valueOf(capacity));
    }

    /**
     * Atomically takes {@code quantity} units.
     *
     * @return units remaining after the take
     * @throws UnknownInventoryException     if the item was never registered
     * @throws InsufficientInventoryException if not enough units are available
     */
    public long take(String inventoryId, int quantity) {
        Long result = redis.execute(
            reserveScript,
            List.of(availableKey(inventoryId)),
            String.valueOf(quantity));

        if (result == null || result == UNKNOWN_KEY) {
            throw new UnknownInventoryException(inventoryId);
        }
        if (result == INSUFFICIENT) {
            throw new InsufficientInventoryException(inventoryId, quantity);
        }
        return result;
    }

    /** Returns units to the pool, clamped at the registered capacity. */
    public void release(String inventoryId, int quantity) {
        Long result = redis.execute(
            releaseScript,
            List.of(availableKey(inventoryId), capacityKey(inventoryId)),
            String.valueOf(quantity));

        if (result == null || result == UNKNOWN_KEY) {
            throw new UnknownInventoryException(inventoryId);
        }
    }

    public long available(String inventoryId) {
        String value = redis.opsForValue().get(availableKey(inventoryId));
        if (value == null) {
            throw new UnknownInventoryException(inventoryId);
        }
        return Long.parseLong(value);
    }

    private static String availableKey(String inventoryId) {
        return "inventory:available:" + inventoryId;
    }

    private static String capacityKey(String inventoryId) {
        return "inventory:capacity:" + inventoryId;
    }
}
