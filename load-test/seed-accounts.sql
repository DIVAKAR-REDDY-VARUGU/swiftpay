-- Load-test account pool: 1000 accounts (ids 1000-1999) with huge balances.
-- Huge balances => every payment settles end-to-end (no insufficient-funds bail-out at the gateway),
-- and spreading across 1000 rows avoids hot-row lock contention on the debit/credit UPDATEs.
INSERT INTO accounts (id, name, balance, currency, updated_at)
SELECT n, 'load-' || n, 1000000000000000, 'INR', now()
FROM generate_series(1000, 1999) AS n
ON CONFLICT (id) DO NOTHING;
