-- Milestone 4: M-Pesa statement import for reconciliation, KRA eTIMS invoices,
-- and sales made offline at the till (synced later).

-- Lines of the M-Pesa statement downloaded from the M-Pesa business portal / app.
-- Matched against the codes cashiers typed, to catch typos and fake codes.
CREATE TABLE mpesa_transactions (
    id            BIGSERIAL PRIMARY KEY,
    receipt_no    VARCHAR(20)   NOT NULL UNIQUE,
    completed_at  TIMESTAMPTZ   NOT NULL,
    amount        NUMERIC(12,2) NOT NULL,
    other_party   VARCHAR(150),
    account       VARCHAR(60),
    details       VARCHAR(200),
    imported_by   BIGINT        NOT NULL REFERENCES users (id),
    imported_at   TIMESTAMPTZ   NOT NULL
);
CREATE INDEX ix_mpesa_transactions_completed ON mpesa_transactions (completed_at);

-- KRA eTIMS invoices: one per sale and one per return (credit note). Queued and
-- sent in the background, so selling never waits for KRA or the internet.
CREATE SEQUENCE etims_invoice_seq START 1;

CREATE TABLE etims_invoices (
    id                  BIGSERIAL PRIMARY KEY,
    invoice_no          BIGINT       NOT NULL UNIQUE,
    kind                VARCHAR(10)  NOT NULL CHECK (kind IN ('SALE', 'REFUND')),
    sale_id             BIGINT       NOT NULL REFERENCES sales (id),
    return_id           BIGINT       REFERENCES sale_returns (id),
    original_invoice_no BIGINT,
    status              VARCHAR(10)  NOT NULL CHECK (status IN ('PENDING', 'SENT', 'REJECTED')),
    attempts            INT          NOT NULL DEFAULT 0,
    next_attempt_at     TIMESTAMPTZ  NOT NULL,
    last_error          VARCHAR(300),
    scu_receipt_no      BIGINT,
    total_receipt_no    BIGINT,
    internal_data       VARCHAR(120),
    receipt_signature   VARCHAR(120),
    scu_id              VARCHAR(40),
    mrc_no              VARCHAR(40),
    scu_date            VARCHAR(20),
    created_at          TIMESTAMPTZ  NOT NULL,
    sent_at             TIMESTAMPTZ
);
CREATE UNIQUE INDEX ux_etims_invoices_sale ON etims_invoices (sale_id) WHERE kind = 'SALE';
CREATE UNIQUE INDEX ux_etims_invoices_return ON etims_invoices (return_id) WHERE return_id IS NOT NULL;
CREATE INDEX ix_etims_invoices_due ON etims_invoices (status, next_attempt_at) WHERE status = 'PENDING';

-- eTIMS needs each product registered as an item (code + KRA item class).
ALTER TABLE products
    ADD COLUMN etims_item_class VARCHAR(10),
    ADD COLUMN etims_registered_at TIMESTAMPTZ;

-- A sale made while the till could not reach the server: when it happened at
-- the till, and when it reached the server.
ALTER TABLE sales
    ADD COLUMN offline BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN synced_at TIMESTAMPTZ;
