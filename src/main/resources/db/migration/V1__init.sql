CREATE TABLE reservation (
    id              UUID         PRIMARY KEY,
    idempotency_key VARCHAR(100) NOT NULL,
    inventory_id    VARCHAR(100) NOT NULL,
    quantity        INTEGER      NOT NULL CHECK (quantity > 0),
    status          VARCHAR(20)  NOT NULL,
    held_until      TIMESTAMPTZ  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    version         BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_reservation_idempotency UNIQUE (idempotency_key)
);

-- Supports the expiry sweeper's "HELD and past due" scan.
CREATE INDEX idx_reservation_status_held_until ON reservation (status, held_until);
CREATE INDEX idx_reservation_inventory ON reservation (inventory_id);

CREATE TABLE outbox_event (
    id           UUID         PRIMARY KEY,
    aggregate_id VARCHAR(100) NOT NULL,
    event_type   VARCHAR(60)  NOT NULL,
    payload      TEXT         NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL,
    published_at TIMESTAMPTZ,
    attempts     INTEGER      NOT NULL DEFAULT 0
);

-- Partial index: the publisher only ever scans unpublished rows, so the index
-- stays small even as the table grows.
CREATE INDEX idx_outbox_unpublished ON outbox_event (created_at) WHERE published_at IS NULL;
