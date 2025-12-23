-- Inventory stock and reservation tracking
CREATE TABLE IF NOT EXISTS inventory_items (
    id          BIGSERIAL PRIMARY KEY,
    product_id  VARCHAR(64) UNIQUE NOT NULL,
    stock       INT NOT NULL,
    reserved    INT NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS inventory_reservations (
    id               BIGSERIAL PRIMARY KEY,
    order_no         VARCHAR(64) NOT NULL,
    order_item_id    BIGINT,
    product_id       VARCHAR(64) NOT NULL,
    quantity         INT NOT NULL,
    status           VARCHAR(32) NOT NULL,
    force_out_of_stock BOOL,
    partial_fail_index INT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_reservations_order_no ON inventory_reservations(order_no);
CREATE INDEX IF NOT EXISTS idx_reservations_product ON inventory_reservations(product_id);

CREATE TABLE IF NOT EXISTS inventory_events (
    id              BIGSERIAL PRIMARY KEY,
    reservation_id  BIGINT NOT NULL REFERENCES inventory_reservations(id) ON DELETE CASCADE,
    event_type      VARCHAR(64) NOT NULL,
    detail          JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_inventory_events_reservation ON inventory_events(reservation_id);
