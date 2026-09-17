package com.abhishekrana.reservation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "reservation")
public class ReservationProperties {

    private Duration holdTtl = Duration.ofMinutes(15);
    private String topic = "reservation-events";
    private final Outbox outbox = new Outbox();
    private final Expiry expiry = new Expiry();

    public static class Outbox {
        private int batchSize = 100;
        private int maxAttempts = 10;

        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
        public int getMaxAttempts() { return maxAttempts; }
        public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
    }

    public static class Expiry {
        private int batchSize = 200;

        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    }

    public Duration getHoldTtl() { return holdTtl; }
    public void setHoldTtl(Duration holdTtl) { this.holdTtl = holdTtl; }
    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }
    public Outbox getOutbox() { return outbox; }
    public Expiry getExpiry() { return expiry; }
}
