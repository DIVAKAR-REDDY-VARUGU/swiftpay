-- SwiftPay schema — the source of truth for the database.
-- Runs ONCE, automatically, the first time the Postgres container initializes.

CREATE TABLE IF NOT EXISTS accounts (
    id          BIGINT          PRIMARY KEY,                                  -- user id
    name        VARCHAR(120)    NOT NULL,
    balance     NUMERIC(18,2)   NOT NULL DEFAULT 0 CHECK (balance >= 0),      -- no overdraft, enforced at the DB (defense-in-depth)
    currency    VARCHAR(3)      NOT NULL DEFAULT 'INR',
    updated_at  TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS transactions (
    id              VARCHAR(80)     PRIMARY KEY,                              -- = transaction_id, also the idempotency key (client-supplied string)
    sender_id       BIGINT          NOT NULL REFERENCES accounts(id),
    receiver_id     BIGINT          NOT NULL REFERENCES accounts(id),
    amount          NUMERIC(18,2)   NOT NULL CHECK (amount > 0),
    currency        VARCHAR(3)      NOT NULL,
    status          VARCHAR(20)     NOT NULL,                                 -- PENDING | COMPLETED | FAILED
    failure_reason  VARCHAR(255),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_tx_sender   ON transactions(sender_id);
CREATE INDEX IF NOT EXISTS idx_tx_receiver ON transactions(receiver_id);

-- seed accounts for testing
INSERT INTO accounts (id, name, balance, currency) VALUES
    (1, 'Alice', 1000.00, 'INR'),
    (2, 'Bob',    500.00, 'INR'),
    (3, 'Carol',    0.00, 'INR')
ON CONFLICT (id) DO NOTHING;
