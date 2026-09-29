-- Milestone 6: cashier shifts with a cash count, and indexes for the reports.

-- A cashier's shift: opened with a float in the drawer, closed with a (blind)
-- count of the cash. What the drawer should hold is worked out from the sales
-- and cash refunds in the shift, and kept with the count at closing time.
CREATE TABLE shifts (
    id             BIGSERIAL PRIMARY KEY,
    cashier_id     BIGINT        NOT NULL REFERENCES users (id),
    opened_at      TIMESTAMPTZ   NOT NULL,
    opening_float  NUMERIC(12,2) NOT NULL CHECK (opening_float >= 0),
    closed_at      TIMESTAMPTZ,
    closed_by      BIGINT        REFERENCES users (id),
    cash_sales     NUMERIC(12,2),
    cash_refunds   NUMERIC(12,2),
    expected_cash  NUMERIC(12,2),
    counted_cash   NUMERIC(12,2) CHECK (counted_cash >= 0),
    difference     NUMERIC(12,2),
    note           VARCHAR(300)
);
-- One open shift per cashier.
CREATE UNIQUE INDEX ux_shifts_open ON shifts (cashier_id) WHERE closed_at IS NULL;
CREATE INDEX ix_shifts_opened ON shifts (opened_at);

-- Reports: cash refunds per cashier, returns per sale line, stock value.
CREATE INDEX ix_sale_returns_processed ON sale_returns (processed_by, created_at);
CREATE INDEX ix_sale_return_lines_line ON sale_return_lines (sale_line_id);
