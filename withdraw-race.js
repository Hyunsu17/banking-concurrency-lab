import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

const WALLET_ID = __ENV.WALLET_ID || '2';
const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const WITHDRAW_URL = __ENV.WITHDRAW_URL || `${BASE}/api/v2/wallets/${WALLET_ID}/withdraw`;

const withdrawSuccess = new Counter('withdraw_success');
const withdrawAmountTotal = new Counter('withdraw_amount_total');

export const options = {
  scenarios: {
    race: {
      executor: 'shared-iterations',
      vus: 100,         // 동시 100 VU
      iterations: 200,  // 총 200회 시도 → 시도 총액 20,000 > 잔액 10,000
      maxDuration: '30s',
    },
  },
};

export default function () {
  const res = http.post(
    WITHDRAW_URL,
    JSON.stringify({ amount: 100 }),
    { headers: { 'Content-Type': 'application/json' } }
  );

  if (res.status === 200) {
    withdrawSuccess.add(1);
    withdrawAmountTotal.add(100);
  }

  check(res, {
    'status is 200, 400 or 409': (r) => r.status === 200 || r.status === 400 || r.status === 409,
  });
}

export function teardown() {
  const res = http.get(`${BASE}/api/wallets/${WALLET_ID}`);
  const body = JSON.parse(res.body);
  console.log(`\n========== 정합성 검증 ==========`);
  console.log(`최종 잔액: ${body.balance}원`);
  console.log(`[정상] 잔액 = 0원, 성공건수 = 100, 성공총액 = 10,000원`);
  console.log(`[깨짐] 잔액 < 0이거나, 성공총액 > 10,000이면 race condition 재현됨`);
  console.log(`=================================\n`);
}
