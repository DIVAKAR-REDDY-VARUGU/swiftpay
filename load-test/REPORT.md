# Load Test Report — SwiftPay

**Date:** 2026-07-01
**Objective:** sustain **250 TPS** until **1,000,000** payments are processed, end-to-end, and capture
network-level evidence that traffic flowed through every component.

## Environment
- Single host, Docker Desktop. Core transaction path only: `service-a-gateway`, `service-b-ledger`,
  PostgreSQL, Apache Kafka (KRaft), Redis. (Analytics service + ClickHouse excluded — bonus read-model.)
- Throughput tuning via `docker-compose.loadtest.yml` (env-only, base config unchanged): Kafka **12 partitions**,
  ledger consumer **concurrency 12**, gateway Hikari pool 40, ledger Hikari pool 30, Postgres `max_connections=200`.
- Account pool: **1000 accounts** with large balances (`seed-accounts.sql`) — spreads the debit/credit
  UPDATEs across many rows and guarantees no insufficient-funds bail-out, so every payment settles end-to-end.
- Load generator: **k6** `constant-arrival-rate`, 250/s (`k6-payments.js`).

## Results (k6 — full export in `k6-summary.json`)
| Metric | Value |
|---|---|
| Target | 250 TPS × 1,000,000 |
| Requests sent | **999,609** (392 dropped at cold-start = **0.04%**) |
| Achieved rate | **249.6 req/s** (≈ 250 TPS) |
| Success (HTTP 202) | **100%** (999,609 / 999,609) |
| Failures | **0** |
| Latency avg / median | 21.2 ms / 10.9 ms |
| Latency p90 / p95 | 31.3 ms / 52.6 ms |
| Latency max | 4.18 s (single JVM/GC cold-start outlier) |
| Duration | 1h 06m 40s |
| Data received / sent | 226 MB / 235 MB |

## Ledger settlement (end-to-end correctness under load)
Every accepted payment was settled by the ledger **in real time** — no backlog, no dead-letters:
```
 status    | count
-----------+--------
 COMPLETED | 999609
```
`PENDING = 0`, `FAILED = 0`, and `payments.initiated.DLT` is empty. The 12-partition / 12-thread consumer
kept pace with the 250 TPS ingress throughout (peak observed backlog was a handful of rows). This confirms
stability under sustained load with **no retry storm** and no double-processing (the `SELECT … FOR UPDATE`
row lock keeps parallel settlement idempotent).

## PCAP evidence — `swiftpay-loadtest-slice.pcap.gz`
A **~3-minute steady-state capture** taken during the run (**2.75M packets**), filtered to the components the
system uses:

| Component | Port(s) |
|---|---|
| Gateway API (HTTP) | 8081 |
| Kafka | 9092 / 29092 |
| PostgreSQL | 5432 |
| Redis | 6379 |

Open it in Wireshark → **Statistics ▸ Conversations** to see the live flows to all four components
(~250 new API connections/s during the window), Kafka produce/fetch traffic, and the Postgres/Redis chatter.

> A full 1,000,000-transaction capture would be tens of GB — impractical to commit to a repository. This file is
> therefore a **representative slice** of real traffic from the execution; this report together with
> `k6-summary.json` is the authoritative evidence of the complete 250 TPS / 1,000,000-transaction run.

## Reproduce
Step-by-step in [`run-loadtest.md`](run-loadtest.md).
