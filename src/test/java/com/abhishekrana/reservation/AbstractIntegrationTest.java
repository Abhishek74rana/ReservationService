package com.abhishekrana.reservation;

import com.redis.testcontainers.RedisContainer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Real Postgres and real Redis, because the behaviour under test is the
 * behaviour of those engines — Lua atomicity, SKIP LOCKED, unique constraints.
 * An in-memory stand-in would pass while the production path is broken.
 */
@SpringBootTest
@Testcontainers
@EmbeddedKafka(partitions = 1, topics = "reservation-events")
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("reservations")
            .withUsername("reservations")
            .withPassword("reservations");

    static final RedisContainer REDIS =
        new RedisContainer(DockerImageName.parse("redis:7-alpine"));

    static {
        // Started manually and shared across every test class, so the containers
        // boot once per build instead of once per class.
        POSTGRES.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.kafka.bootstrap-servers", () -> System.getProperty("spring.embedded.kafka.brokers"));
    }
}
