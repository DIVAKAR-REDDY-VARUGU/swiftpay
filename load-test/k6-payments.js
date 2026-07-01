// Load test: drive the gateway at 250 TPS for 1,000,000 payments.
// Run (as a container on the compose network):
//   docker run --rm --network swiftpay_default -e GATEWAY=http://service-a-gateway:8081 \
//       -v "$PWD/load-test":/scripts grafana/k6 run /scripts/k6-payments.js \
//       --summary-export=/scripts/k6-summary.json
import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';

export const options = {
  discardResponseBodies: true,
  scenarios: {
    payments: {
      executor: 'constant-arrival-rate',
      rate: 250,               // 250 requests/sec
      timeUnit: '1s',
      duration: __ENV.DURATION || '4000s',   // 250 * 4000 = 1,000,000 requests (override for a smoke run)
      preAllocatedVUs: 100,
      maxVUs: 500,
    },
  },
  // for a quick smoke run: override with  -e SMOKE=1  (30s ~= 7,500 requests)
};

const GATEWAY = __ENV.GATEWAY || 'http://service-a-gateway:8081';
const MIN_ID = 1000;
const POOL = 1000;             // account ids 1000..1999 (seeded by seed-accounts.sql)

function pick() {
  return MIN_ID + Math.floor(Math.random() * POOL);
}

export default function () {
  const sender = pick();
  let receiver = pick();
  while (receiver === sender) receiver = pick();

  const body = JSON.stringify({
    transactionId: 'TXN-' + exec.scenario.iterationInTest,   // unique across the whole test
    senderId: sender,
    receiverId: receiver,
    amount: 1 + Math.floor(Math.random() * 100),
    currency: 'INR',
  });

  const res = http.post(`${GATEWAY}/v1/payments`, body, {
    headers: { 'Content-Type': 'application/json' },
  });
  check(res, { 'accepted (202)': (r) => r.status === 202 });
}
