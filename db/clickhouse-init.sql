-- SwiftPay analytics store (ClickHouse). Runs once when the container first initializes.
CREATE DATABASE IF NOT EXISTS swiftpay;

CREATE TABLE IF NOT EXISTS swiftpay.payment_events
(
    transaction_id String,
    sender_id      UInt64,
    receiver_id    UInt64,
    amount         Decimal(18, 2),
    status         LowCardinality(String),   -- COMPLETED | FAILED
    reason         String DEFAULT '',
    event_time     DateTime DEFAULT now()
)
ENGINE = ReplacingMergeTree(event_time)
ORDER BY (transaction_id);                    -- dedups redelivered events by transaction_id on merge
