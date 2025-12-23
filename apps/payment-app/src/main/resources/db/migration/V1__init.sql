-- Payments and retry/attempt tracking
CREATE TABLE IF NOT EXISTS payments (
    id           BIGSERIAL PRIMARY KEY,
    order_no     VARCHAR(64) UNIQUE NOT NULL,
    status       VARCHAR(32) NOT NULL,
    amount       NUMERIC(15,2) NOT NULL,
    currency     VARCHAR(8) NOT NULL,
    method       VARCHAR(32),
    fail_rate    NUMERIC(4,3),
    delay_ms     INT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS payment_attempts (
    id            BIGSERIAL PRIMARY KEY,
    payment_id    BIGINT NOT NULL REFERENCES payments(id) ON DELETE CASCADE,
    attempt_no    INT NOT NULL,
    status        VARCHAR(32) NOT NULL,
    error_code    VARCHAR(64),
    error_message TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (payment_id, attempt_no)
);
CREATE INDEX IF NOT EXISTS idx_payment_attempts_payment ON payment_attempts(payment_id);

-- External events (bank transfer confirmation, etc.)
CREATE TABLE IF NOT EXISTS payment_events (
    id           BIGSERIAL PRIMARY KEY,
    payment_id   BIGINT NOT NULL REFERENCES payments(id) ON DELETE CASCADE,
    event_type   VARCHAR(64) NOT NULL,
    payload      JSONB,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_payment_events_payment ON payment_events(payment_id);
