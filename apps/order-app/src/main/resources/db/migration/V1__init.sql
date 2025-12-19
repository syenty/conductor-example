-- Orders and related tables
CREATE TABLE IF NOT EXISTS orders (
    id              BIGSERIAL PRIMARY KEY,
    order_no        VARCHAR(64) UNIQUE NOT NULL,
    status          VARCHAR(32) NOT NULL,
    total_amount    NUMERIC(15,2) NOT NULL,
    currency        VARCHAR(8) NOT NULL,
    customer_id     VARCHAR(64) NOT NULL,
    payment_method  VARCHAR(32),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS order_items (
    id              BIGSERIAL PRIMARY KEY,
    order_id        BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id      VARCHAR(64) NOT NULL,
    quantity        INT NOT NULL,
    unit_price      NUMERIC(15,2) NOT NULL,
    status          VARCHAR(32),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_order_items_order ON order_items(order_id);

CREATE TABLE IF NOT EXISTS order_events (
    id          BIGSERIAL PRIMARY KEY,
    order_id    BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    type        VARCHAR(64) NOT NULL,
    detail      JSONB,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_order_events_order ON order_events(order_id);

-- Manual approval tracking (W10)
CREATE TABLE IF NOT EXISTS approvals (
    id          BIGSERIAL PRIMARY KEY,
    order_no    VARCHAR(64) NOT NULL,
    status      VARCHAR(32) NOT NULL,
    requested_by VARCHAR(64),
    comment     TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_approvals_order_no ON approvals(order_no);
