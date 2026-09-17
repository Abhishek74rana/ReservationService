package com.abhishekrana.reservation.outbox;

import com.abhishekrana.reservation.config.ReservationProperties;
import com.abhishekrana.reservation.domain.OutboxEvent;
import com.abhishekrana.reservation.repo.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Drains the outbox table into Kafka.
 *
 * Delivery is at-least-once: a broker acknowledgement that arrives after the row
 * update fails will cause a redelivery on the next pass. Consumers therefore
 * de-duplicate on the eventId carried in the payload. Ordering per reservation
 * is preserved by keying the Kafka record on the aggregate id, which pins all
 * events for one reservation to a single partition.
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxEventRepository outbox;
    private final KafkaTemplate<String, String> kafka;
    private final ReservationProperties props;

    public OutboxPublisher(OutboxEventRepository outbox,
                           KafkaTemplate<String, String> kafka,
                           ReservationProperties props) {
        this.outbox = outbox;
        this.kafka = kafka;
        this.props = props;
    }

    @Scheduled(fixedDelayString = "${reservation.outbox.poll-interval-ms:1000}")
    @Transactional
    public void publishBatch() {
        List<OutboxEvent> batch = outbox.lockUnpublished(
            props.getOutbox().getBatchSize(), props.getOutbox().getMaxAttempts());

        if (batch.isEmpty()) {
            return;
        }

        for (OutboxEvent event : batch) {
            try {
                kafka.send(props.getTopic(), event.getAggregateId(), event.getPayload())
                    .get();   // block: we must know the outcome before updating the row
                event.markPublished();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Interrupted while publishing outbox event {}", event.getId());
                return;
            } catch (Exception e) {
                event.markAttemptFailed();
                log.error("Failed to publish outbox event {} (attempt {})",
                    event.getId(), event.getAttempts(), e);
            }
        }
    }
}
