-- Social media: the shop's linked accounts and posts (picture + text) sent to them.

-- One linked account per network. Access tokens are stored encrypted
-- (AES-GCM, key from SOCIAL_TOKEN_KEY), never in plain text. WhatsApp keeps
-- only the shop's business number (WhatsApp has no API for posting).
CREATE TABLE social_accounts (
    network           VARCHAR(20)   PRIMARY KEY
                      CHECK (network IN ('FACEBOOK', 'INSTAGRAM', 'TIKTOK', 'WHATSAPP')),
    external_id       VARCHAR(100)  NOT NULL,
    name              VARCHAR(150)  NOT NULL,
    access_token      VARCHAR(2000),
    refresh_token     VARCHAR(2000),
    token_expires_at  TIMESTAMPTZ,
    linked_by         BIGINT        NOT NULL REFERENCES users (id),
    linked_at         TIMESTAMPTZ   NOT NULL
);

-- The random "state" sent to Facebook/TikTok when linking, checked when they
-- send the owner back, so nobody else can link their account to the shop.
CREATE TABLE social_oauth_states (
    state       VARCHAR(64)  PRIMARY KEY,
    provider    VARCHAR(10)  NOT NULL CHECK (provider IN ('META', 'TIKTOK')),
    user_id     BIGINT       NOT NULL REFERENCES users (id),
    created_at  TIMESTAMPTZ  NOT NULL
);

CREATE TABLE social_posts (
    id            BIGSERIAL     PRIMARY KEY,
    title         VARCHAR(150)  NOT NULL,
    description   VARCHAR(2000),
    caption       VARCHAR(2500) NOT NULL,
    image         BYTEA         NOT NULL,
    -- Instagram and TikTok fetch the picture from a public link; this random
    -- token is that link, so pictures can't be guessed by number.
    image_token   VARCHAR(64)   NOT NULL UNIQUE,
    image_width   INT           NOT NULL,
    image_height  INT           NOT NULL,
    created_by    BIGINT        NOT NULL REFERENCES users (id),
    created_at    TIMESTAMPTZ   NOT NULL
);
CREATE INDEX ix_social_posts_created ON social_posts (created_at DESC);

-- Each network a post goes to, sent in the background with retries.
CREATE TABLE social_post_targets (
    id               BIGSERIAL     PRIMARY KEY,
    post_id          BIGINT        NOT NULL REFERENCES social_posts (id),
    network          VARCHAR(20)   NOT NULL CHECK (network IN ('FACEBOOK', 'INSTAGRAM', 'TIKTOK')),
    status           VARCHAR(20)   NOT NULL
                     CHECK (status IN ('PENDING', 'POSTED', 'SENT_TO_INBOX', 'FAILED')),
    attempts         INT           NOT NULL DEFAULT 0,
    next_attempt_at  TIMESTAMPTZ   NOT NULL,
    container_id     VARCHAR(100),
    external_id      VARCHAR(100),
    error            VARCHAR(300),
    posted_at        TIMESTAMPTZ,
    UNIQUE (post_id, network)
);
CREATE INDEX ix_social_post_targets_due ON social_post_targets (next_attempt_at) WHERE status = 'PENDING';
