# Load test — 250 TPS · 1,000,000 transactions · PCAP capture

Drives the **Transaction Gateway** at **250 requests/second** until **1,000,000** payments are sent, and captures a
network trace proving the traffic flowed through every component (API, Kafka, Postgres, Redis).

Only the **core transaction path** is loaded: `postgres, kafka, redis, service-a-gateway, service-b-ledger`.
(The analytics service + ClickHouse are the bonus read-model and are left out of the run.)

## Steps

**1. Start the tuned core stack (fresh):**
```bash
docker compose down -v
docker compose -f docker-compose.yml -f docker-compose.loadtest.yml up -d --build \
  postgres kafka redis service-a-gateway service-b-ledger
docker compose ps        # wait until healthy + both apps "Started"
```

**2. Seed the account pool** (1000 accounts with huge balances so every payment settles):
```bash
docker compose exec -T postgres psql -U swiftpay -d swiftpay < load-test/seed-accounts.sql
```

**3. Run the load** (~67 min). From the repo root:
```bash
docker run --rm --network swiftpay_default -e GATEWAY=http://service-a-gateway:8081 \
  -v "$PWD/load-test":/scripts grafana/k6 run /scripts/k6-payments.js \
  --summary-export=/scripts/k6-summary.json
```

**4. Capture a 3-minute slice** — in a second terminal, ~30s after k6 starts (steady state):
```bash
./load-test/capture.sh 180
```

**5. Verify the ledger drained:**
```bash
docker compose exec -T postgres psql -U swiftpay -d swiftpay \
  -c "SELECT status, count(*) FROM transactions GROUP BY status;"
```

**6. Compress the capture** (commit the `.gz`):
```bash
gzip -f load-test/swiftpay-loadtest-slice.pcap
```

Results (throughput, latency, success rate) are in [`k6-summary.json`](k6-summary.json) and summarised in
[`REPORT.md`](REPORT.md). Open `swiftpay-loadtest-slice.pcap.gz` in Wireshark → **Statistics ▸ Conversations**
to see the live flows to the gateway, Kafka, Postgres, and Redis.
