import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';

const consistencySuccess = new Counter('consistency_success');
const consistencyConflict = new Counter('consistency_conflict_409');
const idempotencySuccess = new Counter('idempotency_success');

export const options = {
  scenarios: {
    // 정합성 테스트: 각 VU가 고유한 키로 출금 (총 시도금액 > 잔액)
    consistency: {
      executor: 'shared-iterations',
      vus: 100,
      iterations: 200,
      maxDuration: '30s',
      exec: 'consistencyTest',
    },
    // 멱등성 테스트: 50 VU가 동일 키로 동시 출금 → 1번만 실행되어야 함
    idempotency: {
      executor: 'shared-iterations',
      vus: 50,
      iterations: 50,
      maxDuration: '30s',
      startTime: '35s',
      exec: 'idempotencyTest',
    },
  },
};

export function setup() {
  const headers = { 'Content-Type': 'application/json' };

  // 정합성용 지갑: 10,000원 (200회 × 100원 시도, 100번만 성공 가능)
  const resA = http.post(`${BASE}/api/user/signUp`, JSON.stringify({ name: 'v3-consistency' }), { headers });
  const walletAId = JSON.parse(resA.body).walletId;
  http.post(`${BASE}/api/v3/wallets/${walletAId}/deposit`, JSON.stringify({ amount: 10000 }),
    { headers: { ...headers, 'Idempotency-Key': 'setup-a-deposit' } });

  // 멱등성용 지갑: 5,000원 (50 VU 동일 키 → 1번만 100원 출금)
  const resB = http.post(`${BASE}/api/user/signUp`, JSON.stringify({ name: 'v3-idempotency' }), { headers });
  const walletBId = JSON.parse(resB.body).walletId;
  http.post(`${BASE}/api/v3/wallets/${walletBId}/deposit`, JSON.stringify({ amount: 5000 }),
    { headers: { ...headers, 'Idempotency-Key': 'setup-b-deposit' } });

  console.log(`[setup] 정합성 지갑 ID=${walletAId} (잔액 10,000원)`);
  console.log(`[setup] 멱등성 지갑 ID=${walletBId} (잔액 5,000원)`);

  return { walletAId, walletBId };
}

export function consistencyTest(data) {
  const key = `consistency-vu${__VU}-iter${__ITER}`;
  const res = http.post(
    `${BASE}/api/v3/wallets/${data.walletAId}/withdraw`,
    JSON.stringify({ amount: 100 }),
    { headers: { 'Content-Type': 'application/json', 'Idempotency-Key': key } }
  );

  if (res.status === 200) consistencySuccess.add(1);
  if (res.status === 409) consistencyConflict.add(1);

  check(res, {
    '[정합성] 허용된 응답': (r) => r.status === 200 || r.status === 400 || r.status === 409,
  });
}

export function idempotencyTest(data) {
  // 모든 VU가 동일한 키 사용 → DB unique 제약으로 최초 1건만 처리
  const res = http.post(
    `${BASE}/api/v3/wallets/${data.walletBId}/withdraw`,
    JSON.stringify({ amount: 100 }),
    { headers: { 'Content-Type': 'application/json', 'Idempotency-Key': 'idempotency-fixed-key-001' } }
  );

  if (res.status === 200) idempotencySuccess.add(1);

  check(res, {
    '[멱등성] 허용된 응답': (r) => r.status === 200 || r.status === 400 || r.status === 409,
  });
}

export function teardown(data) {
  const resA = http.get(`${BASE}/api/wallets/${data.walletAId}`);
  const resB = http.get(`${BASE}/api/wallets/${data.walletBId}`);
  const balanceA = JSON.parse(resA.body).balance;
  const balanceB = JSON.parse(resB.body).balance;

  console.log('\n========== V3 정합성 테스트 결과 (낙관적락 + 고유 멱등성키) ==========');
  console.log(`최종 잔액: ${balanceA}원   기대값: 0원 이상 (잔액 음수 = 정합성 깨짐)`);
  console.log(balanceA >= 0 ? `  ✓ PASS — 잔액 ${balanceA}원 (음수 없음, 정합성 유지)` : `  ✗ FAIL — 잔액이 ${balanceA}원 (음수 → 정합성 깨짐)`);

  console.log('\n========== V3 멱등성 테스트 결과 (50 VU 동일 키 동시 요청) ==========');
  console.log(`최종 잔액: ${balanceB}원   기대값: 4,900원 (1번만 출금)`);
  console.log(balanceB === 4900 ? '  ✓ PASS — 잔액 4,900원 (멱등성 보장됨)' : `  ✗ FAIL — 잔액이 ${balanceB}원 (중복 처리 발생)`);
  console.log('=====================================================================\n');
}
