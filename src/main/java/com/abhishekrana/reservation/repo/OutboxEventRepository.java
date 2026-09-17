package com.abhishekrana.reservation.repo;

import com.abhishekrana.reservation.domain.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    @Query(value = """
        SELECT * FROM outbox_event
        WHERE published_at IS NULL AND attempts < :maxAttempts
        ORDER BY created_at
        LIMIT :limit
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<OutboxEvent> lockUnpublished(@Param("limit") int limit, @Param("maxAttempts") int maxAttempts);
}
