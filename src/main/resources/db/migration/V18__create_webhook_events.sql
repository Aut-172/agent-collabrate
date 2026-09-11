CREATE TABLE webhook_events (
    id BIGSERIAL PRIMARY KEY,
    provider VARCHAR(30) NOT NULL,
    delivery_id VARCHAR(200) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    signature_valid BOOLEAN NOT NULL,
    payload_hash VARCHAR(128) NOT NULL,
    processing_status VARCHAR(30) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    processed_at TIMESTAMPTZ,
    CONSTRAINT webhook_events_status_check CHECK (processing_status IN ('RECEIVED', 'PROCESSED', 'FAILED', 'IGNORED')),
    CONSTRAINT webhook_events_provider_delivery_unique UNIQUE (provider, delivery_id)
);

CREATE INDEX idx_webhook_events_received ON webhook_events(received_at DESC);
