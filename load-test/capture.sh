#!/usr/bin/env bash
# Capture a steady-state slice of the load test's network traffic (API, Kafka, Postgres, Redis).
# A privileged tcpdump runs in the Docker VM's host network namespace, so it sees the bridge
# networks where the containers talk to each other.
#   Usage:  ./capture.sh [seconds]      (default 180 = 3 minutes)
set -euo pipefail
SECONDS_TO_CAPTURE="${1:-180}"
OUT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "Capturing ${SECONDS_TO_CAPTURE}s of traffic (ports 8081 API, 9092/29092 Kafka, 5432 Postgres, 6379 Redis)..."
docker run --rm --net=host --privileged \
  -v "${OUT_DIR}:/cap" \
  nicolaka/netshoot \
  timeout "${SECONDS_TO_CAPTURE}" \
  tcpdump -i any -s 96 \
    'tcp port 8081 or tcp port 29092 or tcp port 9092 or tcp port 5432 or tcp port 6379' \
    -w /cap/swiftpay-loadtest-slice.pcap

echo "Done -> ${OUT_DIR}/swiftpay-loadtest-slice.pcap"
