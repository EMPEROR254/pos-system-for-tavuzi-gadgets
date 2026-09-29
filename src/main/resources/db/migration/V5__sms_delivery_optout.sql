-- Milestone 5: SMS delivery reports, "Reply STOP" opt-outs, scheduled promotions
-- and quiet hours.

-- When a message may go out (scheduled promotions, quiet hours), and what the
-- network reported back about delivery.
ALTER TABLE sms_messages
    ADD COLUMN send_after      TIMESTAMPTZ,
    ADD COLUMN delivery_status VARCHAR(30),
    ADD COLUMN failure_reason  VARCHAR(100),
    ADD COLUMN delivered_at    TIMESTAMPTZ;
DROP INDEX ix_sms_messages_queue;
CREATE INDEX ix_sms_messages_queue ON sms_messages (status, send_after, id) WHERE status = 'QUEUED';
CREATE INDEX ix_sms_messages_provider_id ON sms_messages (provider_message_id);
CREATE INDEX ix_sms_messages_campaign ON sms_messages (campaign_id);

ALTER TABLE sms_campaigns ADD COLUMN send_at TIMESTAMPTZ;

-- Phone numbers that replied STOP (or opted out with the network). They never
-- get promotions again unless the customer agrees again at the till.
CREATE TABLE sms_opt_outs (
    phone       VARCHAR(15) PRIMARY KEY,
    source      VARCHAR(20) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL
);

-- Replies customers sent to the shop's SMS number (kept for the owner to read).
CREATE TABLE sms_inbound (
    id           BIGSERIAL PRIMARY KEY,
    provider_id  VARCHAR(80) UNIQUE,
    phone        VARCHAR(15) NOT NULL,
    text         VARCHAR(500) NOT NULL,
    opted_out    BOOLEAN      NOT NULL,
    received_at  TIMESTAMPTZ  NOT NULL
);
CREATE INDEX ix_sms_inbound_received ON sms_inbound (received_at DESC);
