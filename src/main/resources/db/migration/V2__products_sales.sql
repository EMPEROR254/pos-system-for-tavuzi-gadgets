-- Milestone 2: products (with selling units and retail/wholesale prices), sales,
-- split payments, sold IMEIs/serials for warranty, and supervised returns.

CREATE TABLE products (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(150)  NOT NULL,
    sku             VARCHAR(50),
    category        VARCHAR(60),
    -- STANDARD = VAT at the configured rate (16%), ZERO_RATED = 0%, EXEMPT = no VAT.
    vat_category    VARCHAR(20)   NOT NULL CHECK (vat_category IN ('STANDARD', 'ZERO_RATED', 'EXEMPT')),
    -- NONE, IMEI (phones: 15 digits) or SERIAL (laptops, TVs): asked for at the till.
    serial_type     VARCHAR(10)   NOT NULL CHECK (serial_type IN ('NONE', 'IMEI', 'SERIAL')),
    warranty_months INT           NOT NULL DEFAULT 0 CHECK (warranty_months >= 0),
    -- Cost of one base unit (e.g. one piece), for profit reports.
    cost_price      NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (cost_price >= 0),
    active          BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_products_sku ON products (lower(sku)) WHERE sku IS NOT NULL;
CREATE INDEX ix_products_name ON products (lower(name));
CREATE INDEX ix_products_category ON products (category);

-- How a product is sold: e.g. "Piece" (factor 1) and "Carton of 20" (factor 20).
CREATE TABLE product_units (
    id              BIGSERIAL PRIMARY KEY,
    product_id      BIGINT        NOT NULL REFERENCES products (id),
    name            VARCHAR(40)   NOT NULL,
    factor          INT           NOT NULL CHECK (factor >= 1),
    barcode         VARCHAR(50),
    retail_price    NUMERIC(12,2) NOT NULL CHECK (retail_price >= 0),
    wholesale_price NUMERIC(12,2) CHECK (wholesale_price >= 0),
    sort_order      INT           NOT NULL DEFAULT 0
);
CREATE INDEX ix_product_units_product ON product_units (product_id);
CREATE UNIQUE INDEX ux_product_units_barcode ON product_units (barcode) WHERE barcode IS NOT NULL;

CREATE SEQUENCE sale_receipt_seq START 1;

CREATE TABLE sales (
    id              BIGSERIAL PRIMARY KEY,
    receipt_no      VARCHAR(20)   NOT NULL UNIQUE,
    -- Chosen by the till; sending the same sale twice (double click, retry) returns the first.
    client_ref      VARCHAR(64)   NOT NULL UNIQUE,
    cashier_id      BIGINT        NOT NULL REFERENCES users (id),
    discount_approved_by BIGINT   REFERENCES users (id),
    price_tier      VARCHAR(10)   NOT NULL CHECK (price_tier IN ('RETAIL', 'WHOLESALE')),
    status          VARCHAR(20)   NOT NULL CHECK (status IN ('COMPLETED', 'PARTLY_RETURNED', 'RETURNED')),
    customer_name   VARCHAR(100),
    customer_phone  VARCHAR(15),
    subtotal        NUMERIC(12,2) NOT NULL,
    discount_total  NUMERIC(12,2) NOT NULL,
    vat_total       NUMERIC(12,2) NOT NULL,
    total           NUMERIC(12,2) NOT NULL,
    paid_total      NUMERIC(12,2) NOT NULL,
    change_due      NUMERIC(12,2) NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL
);
CREATE INDEX ix_sales_created_at ON sales (created_at);
CREATE INDEX ix_sales_cashier ON sales (cashier_id, created_at);
CREATE INDEX ix_sales_status ON sales (status);

-- Names, prices and VAT are copied onto the line so old receipts never change.
CREATE TABLE sale_lines (
    id                BIGSERIAL PRIMARY KEY,
    sale_id           BIGINT        NOT NULL REFERENCES sales (id),
    line_no           INT           NOT NULL,
    product_id        BIGINT        NOT NULL REFERENCES products (id),
    unit_id           BIGINT        NOT NULL,
    product_name      VARCHAR(150)  NOT NULL,
    unit_name         VARCHAR(40)   NOT NULL,
    unit_factor       INT           NOT NULL,
    quantity          INT           NOT NULL CHECK (quantity > 0),
    returned_quantity INT           NOT NULL DEFAULT 0,
    unit_price        NUMERIC(12,2) NOT NULL,
    discount          NUMERIC(12,2) NOT NULL DEFAULT 0,
    line_total        NUMERIC(12,2) NOT NULL,
    vat_category      VARCHAR(20)   NOT NULL,
    vat_rate          NUMERIC(5,2)  NOT NULL,
    vat_amount        NUMERIC(12,2) NOT NULL,
    unit_cost         NUMERIC(12,2) NOT NULL,
    warranty_months   INT           NOT NULL DEFAULT 0,
    warranty_ends_at  TIMESTAMPTZ
);
CREATE INDEX ix_sale_lines_sale ON sale_lines (sale_id);
CREATE INDEX ix_sale_lines_product ON sale_lines (product_id);

CREATE TABLE payments (
    id          BIGSERIAL PRIMARY KEY,
    sale_id     BIGINT        NOT NULL REFERENCES sales (id),
    method      VARCHAR(20)   NOT NULL CHECK (method IN ('CASH', 'MPESA_TILL', 'MPESA_PAYBILL', 'CARD')),
    amount      NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    reference   VARCHAR(40),
    created_at  TIMESTAMPTZ   NOT NULL
);
CREATE INDEX ix_payments_sale ON payments (sale_id);
CREATE INDEX ix_payments_method_created ON payments (method, created_at);
-- An M-Pesa code can pay for only one sale (stops the same SMS being used twice).
CREATE UNIQUE INDEX ux_payments_mpesa_code ON payments (reference)
    WHERE method IN ('MPESA_TILL', 'MPESA_PAYBILL');

-- Each IMEI/serial sold, for warranty checks. A phone can't be sold twice unless it was returned.
CREATE TABLE sold_serials (
    id            BIGSERIAL PRIMARY KEY,
    sale_line_id  BIGINT       NOT NULL REFERENCES sale_lines (id),
    serial        VARCHAR(40)  NOT NULL,
    returned_at   TIMESTAMPTZ
);
CREATE INDEX ix_sold_serials_line ON sold_serials (sale_line_id);
CREATE INDEX ix_sold_serials_serial ON sold_serials (upper(serial));
CREATE UNIQUE INDEX ux_sold_serials_active ON sold_serials (upper(serial)) WHERE returned_at IS NULL;

-- Returns and exchanges: only with the owner's approval (spec).
CREATE TABLE sale_returns (
    id             BIGSERIAL PRIMARY KEY,
    sale_id        BIGINT        NOT NULL REFERENCES sales (id),
    processed_by   BIGINT        NOT NULL REFERENCES users (id),
    approved_by    BIGINT        NOT NULL REFERENCES users (id),
    reason         VARCHAR(300)  NOT NULL,
    refund_method  VARCHAR(20)   NOT NULL CHECK (refund_method IN ('CASH', 'MPESA_TILL', 'MPESA_PAYBILL', 'CARD')),
    refund_amount  NUMERIC(12,2) NOT NULL,
    vat_amount     NUMERIC(12,2) NOT NULL,
    created_at     TIMESTAMPTZ   NOT NULL
);
CREATE INDEX ix_sale_returns_sale ON sale_returns (sale_id);
CREATE INDEX ix_sale_returns_created ON sale_returns (created_at);

CREATE TABLE sale_return_lines (
    id            BIGSERIAL PRIMARY KEY,
    return_id     BIGINT        NOT NULL REFERENCES sale_returns (id),
    sale_line_id  BIGINT        NOT NULL REFERENCES sale_lines (id),
    quantity      INT           NOT NULL CHECK (quantity > 0),
    amount        NUMERIC(12,2) NOT NULL,
    vat_amount    NUMERIC(12,2) NOT NULL
);
CREATE INDEX ix_sale_return_lines_return ON sale_return_lines (return_id);
