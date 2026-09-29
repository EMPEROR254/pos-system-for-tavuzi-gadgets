-- Milestone 3: stock (levels, movements, IMEIs in stock, suppliers and purchases,
-- stock counts), customers, SMS messages, and printer settings.

-- Stock is kept in base units (e.g. pieces); a carton of 20 sold takes 20 off.
ALTER TABLE products
    ADD COLUMN stock_on_hand INT NOT NULL DEFAULT 0,
    ADD COLUMN reorder_level INT NOT NULL DEFAULT 0 CHECK (reorder_level >= 0);
CREATE INDEX ix_products_low_stock ON products (stock_on_hand, reorder_level) WHERE active;

-- Every change to stock, with the balance after it: the product's stock history.
CREATE TABLE stock_movements (
    id            BIGSERIAL PRIMARY KEY,
    product_id    BIGINT       NOT NULL REFERENCES products (id),
    type          VARCHAR(20)  NOT NULL CHECK (type IN
                  ('SALE', 'RETURN', 'PURCHASE', 'ADJUSTMENT', 'COUNT')),
    quantity      INT          NOT NULL,
    balance_after INT          NOT NULL,
    reason        VARCHAR(30),
    note          VARCHAR(200),
    reference     VARCHAR(40),
    user_id       BIGINT       NOT NULL REFERENCES users (id),
    created_at    TIMESTAMPTZ  NOT NULL
);
CREATE INDEX ix_stock_movements_product ON stock_movements (product_id, created_at DESC);
CREATE INDEX ix_stock_movements_created ON stock_movements (created_at);

-- Phones, laptops and TVs in stock by IMEI/serial. The till can only sell an
-- IMEI that is IN_STOCK; a return puts it back.
CREATE TABLE stock_serials (
    id          BIGSERIAL PRIMARY KEY,
    product_id  BIGINT      NOT NULL REFERENCES products (id),
    serial      VARCHAR(40) NOT NULL,
    status      VARCHAR(10) NOT NULL CHECK (status IN ('IN_STOCK', 'SOLD', 'REMOVED')),
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL
);
CREATE UNIQUE INDEX ux_stock_serials_serial ON stock_serials (upper(serial));
CREATE INDEX ix_stock_serials_product ON stock_serials (product_id, status);

CREATE TABLE suppliers (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(120) NOT NULL,
    phone       VARCHAR(15),
    email       VARCHAR(120),
    notes       VARCHAR(300),
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL
);
CREATE UNIQUE INDEX ux_suppliers_name ON suppliers (lower(name));

CREATE TABLE purchases (
    id              BIGSERIAL PRIMARY KEY,
    supplier_id     BIGINT        NOT NULL REFERENCES suppliers (id),
    invoice_no      VARCHAR(40),
    total           NUMERIC(12,2) NOT NULL,
    received_by     BIGINT        NOT NULL REFERENCES users (id),
    received_at     TIMESTAMPTZ   NOT NULL,
    note            VARCHAR(300)
);
CREATE INDEX ix_purchases_supplier ON purchases (supplier_id, received_at DESC);
CREATE INDEX ix_purchases_received ON purchases (received_at);

CREATE TABLE purchase_lines (
    id           BIGSERIAL PRIMARY KEY,
    purchase_id  BIGINT        NOT NULL REFERENCES purchases (id),
    product_id   BIGINT        NOT NULL REFERENCES products (id),
    unit_name    VARCHAR(40)   NOT NULL,
    unit_factor  INT           NOT NULL,
    quantity     INT           NOT NULL CHECK (quantity > 0),
    unit_cost    NUMERIC(12,2) NOT NULL CHECK (unit_cost >= 0),
    line_total   NUMERIC(12,2) NOT NULL
);
CREATE INDEX ix_purchase_lines_purchase ON purchase_lines (purchase_id);

-- Money paid to suppliers. What the shop owes = purchases - payments.
CREATE TABLE supplier_payments (
    id           BIGSERIAL PRIMARY KEY,
    supplier_id  BIGINT        NOT NULL REFERENCES suppliers (id),
    purchase_id  BIGINT        REFERENCES purchases (id),
    amount       NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    method       VARCHAR(20)   NOT NULL CHECK (method IN ('CASH', 'MPESA', 'BANK', 'CHEQUE')),
    reference    VARCHAR(40),
    paid_by      BIGINT        NOT NULL REFERENCES users (id),
    paid_at      TIMESTAMPTZ   NOT NULL
);
CREATE INDEX ix_supplier_payments_supplier ON supplier_payments (supplier_id, paid_at DESC);

-- Weekly stock counts: expected is taken when the count starts; on finishing,
-- the difference is applied to the current stock (so sales during the count are kept).
CREATE TABLE stock_counts (
    id           BIGSERIAL PRIMARY KEY,
    status       VARCHAR(10)  NOT NULL CHECK (status IN ('OPEN', 'FINISHED', 'CANCELLED')),
    category     VARCHAR(60),
    started_by   BIGINT       NOT NULL REFERENCES users (id),
    started_at   TIMESTAMPTZ  NOT NULL,
    finished_by  BIGINT       REFERENCES users (id),
    finished_at  TIMESTAMPTZ
);
CREATE UNIQUE INDEX ux_stock_counts_one_open ON stock_counts (status) WHERE status = 'OPEN';

CREATE TABLE stock_count_lines (
    id          BIGSERIAL PRIMARY KEY,
    count_id    BIGINT        NOT NULL REFERENCES stock_counts (id),
    product_id  BIGINT        NOT NULL REFERENCES products (id),
    expected    INT           NOT NULL,
    counted     INT           CHECK (counted >= 0),
    unit_cost   NUMERIC(12,2) NOT NULL
);
CREATE UNIQUE INDEX ux_stock_count_lines ON stock_count_lines (count_id, product_id);

CREATE TABLE customers (
    id               BIGSERIAL PRIMARY KEY,
    name             VARCHAR(100) NOT NULL,
    phone            VARCHAR(15)  NOT NULL,
    email            VARCHAR(120),
    notes            VARCHAR(300),
    -- Kenya Data Protection Act: promotional SMS only with the customer's consent.
    marketing_opt_in BOOLEAN      NOT NULL DEFAULT FALSE,
    opt_in_at        TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL
);
CREATE UNIQUE INDEX ux_customers_phone ON customers (phone);
CREATE INDEX ix_customers_name ON customers (lower(name));

ALTER TABLE sales ADD COLUMN customer_id BIGINT REFERENCES customers (id);
CREATE INDEX ix_sales_customer ON sales (customer_id, created_at DESC);

CREATE TABLE sms_campaigns (
    id          BIGSERIAL PRIMARY KEY,
    message     VARCHAR(480) NOT NULL,
    audience    VARCHAR(200) NOT NULL,
    recipients  INT          NOT NULL,
    created_by  BIGINT       NOT NULL REFERENCES users (id),
    created_at  TIMESTAMPTZ  NOT NULL
);

-- Outbox: messages are saved first and sent in the background, so the till
-- never waits for the SMS provider; failed sends are retried a few times.
CREATE TABLE sms_messages (
    id                   BIGSERIAL PRIMARY KEY,
    phone                VARCHAR(15)  NOT NULL,
    body                 VARCHAR(480) NOT NULL,
    kind                 VARCHAR(10)  NOT NULL CHECK (kind IN ('RECEIPT', 'PROMO', 'TEST')),
    status               VARCHAR(10)  NOT NULL CHECK (status IN ('QUEUED', 'SENT', 'FAILED')),
    attempts             INT          NOT NULL DEFAULT 0,
    provider_message_id  VARCHAR(80),
    error                VARCHAR(200),
    customer_id          BIGINT       REFERENCES customers (id),
    sale_id              BIGINT       REFERENCES sales (id),
    campaign_id          BIGINT       REFERENCES sms_campaigns (id),
    created_at           TIMESTAMPTZ  NOT NULL,
    sent_at              TIMESTAMPTZ
);
CREATE INDEX ix_sms_messages_queue ON sms_messages (status, id) WHERE status = 'QUEUED';
CREATE INDEX ix_sms_messages_created ON sms_messages (created_at DESC);

-- One row: the receipt printer the owner set up (overrides the .env defaults).
CREATE TABLE printer_settings (
    id           INT          PRIMARY KEY CHECK (id = 1),
    host         VARCHAR(100),
    port         INT          NOT NULL DEFAULT 9100,
    width        INT          NOT NULL DEFAULT 48 CHECK (width IN (32, 42, 48)),
    open_drawer  BOOLEAN      NOT NULL DEFAULT FALSE,
    auto_print   BOOLEAN      NOT NULL DEFAULT FALSE,
    updated_at   TIMESTAMPTZ  NOT NULL
);
