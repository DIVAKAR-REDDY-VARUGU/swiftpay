# 💸 SwiftPay — Real-Time Payment Ledger

A resilient, event-driven peer-to-peer (P2P) payment platform. A **Transaction Gateway** accepts payment requests and a **Ledger Service** settles them asynchronously over Kafka, with Redis-backed **idempotency**, an **atomic, race-safe** balance transfer, and full audit history for reporting.

## Architecture

```
Client ──POST /v1/payments──► TRANSACTION GATEWAY (REST, :8081)
   1. Idempotency  — Redis SET NX EX 86400 (+ Postgres PK)  → duplicate? return existing
   2. Validate     — accounts exist, sender ≠ receiver, balance ≥ amount (Redis-cached lookup)
   3. Persist      — transaction row in Postgres (status = PENDING)
   4. Publish      — "PaymentInitiated" → Kafka (topic: payments.initiated)
   5. Respond      — 202 Accepted { transactionId, PENDING }
        │
        ▼  Kafka
LEDGER SERVICE (consumer, :8082)
   6. Consume PaymentInitiated  (at-least-once → idempotent processing)
   7. DB TRANSACTION: debit sender WHERE balance ≥ amount  +  credit receiver   (atomic, race-safe)
   8. Status → COMPLETED, or FAILED("insufficient funds") if the debit can't apply
   9. Publish "PaymentOutcome" (COMPLETED / FAILED) → Kafka (topic: payments.completed)
        │
        ▼  Kafka
ANALYTICS SERVICE (consumer, :8083)
  10. Consume PaymentOutcome on its OWN consumer group  →  insert into ClickHouse (append-only)
  11. Serve read-only analytics: GET /v1/analytics/{summary, top-senders, users/{id}}

Infrastructure:  PostgreSQL · Apache Kafka (KRaft) · Redis · ClickHouse
```

## Tech stack
Java 21 · Spring Boot 3.3 · PostgreSQL 16 · Apache Kafka (KRaft) · Redis 7 · ClickHouse · Spring Data JPA · Spring Kafka · Maven · Docker / Docker Compose · Swagger/OpenAPI · GitHub Actions.

## Design decisions
1. **Idempotency** — `transaction_id` is the idempotency key. A Redis `SET NX` with a 24-hour TTL is the fast guard; the Postgres primary key is the durable backstop. A duplicate request returns the original result, so client retries are safe.
2. **Asynchronous settlement (eventual consistency)** — the gateway responds `202 PENDING` immediately and publishes an event; the ledger settles the money asynchronously. This decouples the services and lets each scale independently.
3. **Atomic, race-safe transfer** — the debit is a single conditional update — `UPDATE accounts SET balance = balance - :amt WHERE id = :sender AND balance >= :amt` — inside a database transaction, backed by a `CHECK (balance >= 0)` constraint. Concurrent payments can never overdraw an account; a no-op update means insufficient funds and the transaction is marked `FAILED`.
4. **Resilience** — the Kafka consumer is at-least-once and the processing is idempotent, so redelivery is safe. Failed messages are retried with back-off and, if still failing, routed to a dead-letter topic (`payments.initiated.DLT`) so a poison message or a temporary outage can't block the partition.
5. **Observability & API standards** — health endpoints (`/actuator/health`), structured logging, correct HTTP status codes, standard error objects, and OpenAPI documentation (Swagger UI).
6. **Analytics read model (CQRS)** — a third service consumes the settlement outcomes on its own Kafka consumer group and writes them to **ClickHouse**, a columnar OLAP store built for fast aggregations over append-only event streams. Transactional writes stay in Postgres; analytical reads are served from ClickHouse — separating the two workloads and demonstrating pub/sub fan-out.

> The gateway and ledger share a single PostgreSQL database for the ledger. A stricter microservice design would give each service its own datastore; a shared ledger DB is a deliberate simplification for this project.

## Project structure
```
swiftpay/
├── docker-compose.yml          # Postgres + Kafka + Redis + both services
├── db/init.sql                 # Postgres schema + seed accounts (Alice=1000, Bob=500, Carol=0)
├── db/clickhouse-init.sql      # ClickHouse analytics table
├── service-a-gateway/          # Transaction Gateway (REST)               :8081
├── service-b-ledger/           # Ledger Service (Kafka consumer)          :8082
└── service-c-analytics/        # Analytics Service (Kafka → ClickHouse)   :8083
```

## Getting started
**Prerequisites:** Docker Desktop. (For local, non-Docker runs you also need JDK 21.)

```bash
# build images and start the whole stack
docker compose up -d --build

# check everything is healthy
docker compose ps

# follow the services
docker compose logs -f service-a-gateway service-b-ledger
```

Then:
```bash
# initiate a payment: Alice (1) pays Bob (2) $100
curl -i -X POST http://localhost:8081/v1/payments \
  -H "Content-Type: application/json" \
  -d '{"senderId":1,"receiverId":2,"amount":100.00,"currency":"INR"}'
#  → 202 Accepted { transactionId, PENDING }   (settles to COMPLETED via the ledger)

# check balances and history (all reads are served by the gateway)
curl http://localhost:8081/v1/accounts/1            # Alice
curl http://localhost:8081/v1/users/1/transactions  # history
```

## API
The **Gateway** is the single public API. The **Ledger** runs as a background Kafka consumer and exposes only a health check.

**Gateway** — Swagger: http://localhost:8081/swagger-ui.html
| Method | Path | Description |
|---|---|---|
| `POST` | `/v1/payments` | Initiate a payment (idempotent, returns `202 PENDING`) |
| `GET`  | `/v1/payments/{id}` | Get a transaction's status |
| `GET`  | `/v1/users/{id}/transactions` | A user's transaction history |
| `GET`  | `/v1/accounts` | All accounts with balances |
| `GET`  | `/v1/accounts/{id}` | One account's balance |

**Ledger** — Swagger: http://localhost:8082/swagger-ui.html
| Method | Path | Description |
|---|---|---|
| `GET` | `/health` | Service health |

**Analytics** — Swagger: http://localhost:8083/swagger-ui.html
| Method | Path | Description |
|---|---|---|
| `GET` | `/v1/analytics/summary` | Completed/failed counts + total settled volume |
| `GET` | `/v1/analytics/top-senders?limit=5` | Top senders by settled volume |
| `GET` | `/v1/analytics/users/{id}` | One user's sent/received totals |
| `GET` | `/health` | Service health |

## Configuration
| Service | Host port |
|---|---|
| Transaction Gateway | 8081 |
| Ledger Service | 8082 |
| Analytics Service | 8083 |
| PostgreSQL | 5433 |
| ClickHouse | 8123 |
| Kafka | 9092 |
| Redis | 6379 |

The application runs in **UTC**. Services read config from environment variables when containerized (`SPRING_DATASOURCE_URL`, `SPRING_KAFKA_BOOTSTRAP_SERVERS`, `SPRING_DATA_REDIS_HOST`).

## Load test & PCAP
The system was load-tested at **250 TPS for 1,000,000 transactions** (~67 min). Result: **999,609 requests at ~250 req/s, 100% accepted, 0 failures**, and **every payment settled to `COMPLETED`** with no backlog or dead-letters. A representative network capture (API · Kafka · Postgres · Redis) is included as evidence.

See [`load-test/`](load-test/) — [`REPORT.md`](load-test/REPORT.md) (results), [`run-loadtest.md`](load-test/run-loadtest.md) (how to reproduce), `k6-payments.js` / `seed-accounts.sql` / `capture.sh`, and `swiftpay-loadtest-slice.pcap.gz` (open in Wireshark).
