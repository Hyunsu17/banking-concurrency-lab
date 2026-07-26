import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

// k6 run test.js                  → 전체 순차 실행 (v1 → v2 → v3 → v4)
// k6 run test.js -e VERSION=v1    → v1만
// k6 run test.js -e VERSION=v2    → v2만
// k6 run test.js -e VERSION=v3    → v3만
// k6 run test.js -e VERSION=v4    → v4만

const BASE    = __ENV.BASE_URL || 'http://localhost:8080';
const VERSION = __ENV.VERSION;          // v1 | v2 | v3 | v4 | (unset = all)
const runAll  = !VERSION || VERSION === 'all';

const v1Success            = new Counter('v1_success');
const v1Insufficient       = new Counter('v1_insufficient_400');
const v1Conflict           = new Counter('v1_conflict_409');
const v2Success            = new Counter('v2_success');
const v2Insufficient       = new Counter('v2_insufficient_400');
const v2Conflict           = new Counter('v2_conflict_409');
const v3ConsistencySuccess = new Counter('v3_consistency_success');
const v3Conflict           = new Counter('v3_conflict_409');
const v3IdempotencySuccess = new Counter('v3_idempotency_success');
const v4Success            = new Counter('v4_success');
const v4Insufficient       = new Counter('v4_insufficient_400');
const v4Conflict           = new Counter('v4_conflict_409');

// 전체 실행 시 순차 오프셋, 개별 실행 시 0s 기준
const startAt = {
  v1:  runAll ? '0s'   : '0s',
  v2:  runAll ? '35s'  : '0s',
  v3c: runAll ? '70s'  : '0s',
  v3i: runAll ? '105s' : '35s',
  v4:  runAll ? '140s' : '0s',
};

const scenarios = {};

if (!VERSION || VERSION === 'v1') {
  scenarios.v1_race = {
    executor: 'shared-iterations',
    vus: 100, iterations: 200, maxDuration: '30s',
    startTime: startAt.v1,
    exec: 'v1Test',
  };
}
if (!VERSION || VERSION === 'v2') {
  scenarios.v2_race = {
    executor: 'shared-iterations',
    vus: 100, iterations: 200, maxDuration: '30s',
    startTime: startAt.v2,
    exec: 'v2Test',
  };
}
if (!VERSION || VERSION === 'v3') {
  scenarios.v3_consistency = {
    executor: 'shared-iterations',
    vus: 100, iterations: 200, maxDuration: '30s',
    startTime: startAt.v3c,
    exec: 'v3ConsistencyTest',
  };
  scenarios.v3_idempotency = {
    executor: 'shared-iterations',
    vus: 50, iterations: 50, maxDuration: '30s',
    startTime: startAt.v3i,
    exec: 'v3IdempotencyTest',
  };
}
if (!VERSION || VERSION === 'v4') {
  scenarios.v4_race = {
    executor: 'shared-iterations',
    vus: 100, iterations: 200, maxDuration: '30s',
    startTime: startAt.v4,
    exec: 'v4Test',
  };
}

export const options = { scenarios };

// ─── Setup ────────────────────────────────────────────────────────────────────

export function setup() {
  const h = { 'Content-Type': 'application/json' };
  const result = {};
  const runId = Date.now(); // 재실행 시 멱등키 충돌 방지 (VU/ITER 번호는 실행마다 재사용됨)
  result.runId = runId;

  if (!VERSION || VERSION === 'v1') {
    const res = http.post(`${BASE}/api/user/signUp`, JSON.stringify({ name: 'test-v1' }), { headers: h });
    result.v1Id = JSON.parse(res.body).walletId;
    http.post(`${BASE}/api/v1/wallets/${result.v1Id}/deposit`, JSON.stringify({ amount: 10000 }), { headers: h });
    console.log(`[setup] v1 wallet=${result.v1Id} (10,000원)`);
  }

  if (!VERSION || VERSION === 'v2') {
    const res = http.post(`${BASE}/api/user/signUp`, JSON.stringify({ name: 'test-v2' }), { headers: h });
    result.v2Id = JSON.parse(res.body).walletId;
    http.post(`${BASE}/api/v2/wallets/${result.v2Id}/deposit`, JSON.stringify({ amount: 10000 }), { headers: h });
    console.log(`[setup] v2 wallet=${result.v2Id} (10,000원)`);
  }

  if (!VERSION || VERSION === 'v3') {
    const resA = http.post(`${BASE}/api/user/signUp`, JSON.stringify({ name: 'test-v3-consistency' }), { headers: h });
    result.v3ConsistencyId = JSON.parse(resA.body).walletId;
    http.post(`${BASE}/api/v3/wallets/${result.v3ConsistencyId}/deposit`, JSON.stringify({ amount: 10000 }),
      { headers: { ...h, 'Idempotency-Key': `setup-v3-consistency-${runId}` } });

    const resB = http.post(`${BASE}/api/user/signUp`, JSON.stringify({ name: 'test-v3-idempotency' }), { headers: h });
    result.v3IdempotencyId = JSON.parse(resB.body).walletId;
    http.post(`${BASE}/api/v3/wallets/${result.v3IdempotencyId}/deposit`, JSON.stringify({ amount: 5000 }),
      { headers: { ...h, 'Idempotency-Key': `setup-v3-idempotency-${runId}` } });

    console.log(`[setup] v3-consistency wallet=${result.v3ConsistencyId} (10,000원)`);
    console.log(`[setup] v3-idempotency wallet=${result.v3IdempotencyId} (5,000원)`);
  }

  if (!VERSION || VERSION === 'v4') {
    const res = http.post(`${BASE}/api/user/signUp`, JSON.stringify({ name: 'test-v4' }), { headers: h });
    result.v4Id = JSON.parse(res.body).walletId;
    http.post(`${BASE}/api/v4/wallets/${result.v4Id}/deposit`, JSON.stringify({ amount: 10000 }), { headers: h });
    console.log(`[setup] v4 wallet=${result.v4Id} (10,000원)`);
  }

  return result;
}

// ─── Test functions ───────────────────────────────────────────────────────────

export function v1Test(data) {
  const res = http.post(
    `${BASE}/api/v1/wallets/${data.v1Id}/withdraw`,
    JSON.stringify({ amount: 100 }),
    { headers: { 'Content-Type': 'application/json' } }
  );
  if (res.status === 200) v1Success.add(1);
  else if (res.status === 400) v1Insufficient.add(1);
  else if (res.status === 409) v1Conflict.add(1);
  check(res, { '[v1] 허용된 응답': (r) => r.status === 200 || r.status === 400 || r.status === 409 });
}

export function v2Test(data) {
  const res = http.post(
    `${BASE}/api/v2/wallets/${data.v2Id}/withdraw`,
    JSON.stringify({ amount: 100 }),
    { headers: { 'Content-Type': 'application/json' } }
  );
  if (res.status === 200) v2Success.add(1);
  else if (res.status === 400) v2Insufficient.add(1);
  else if (res.status === 409) v2Conflict.add(1);
  check(res, { '[v2] 허용된 응답': (r) => r.status === 200 || r.status === 400 || r.status === 409 });
}

export function v3ConsistencyTest(data) {
  const res = http.post(
    `${BASE}/api/v3/wallets/${data.v3ConsistencyId}/withdraw`,
    JSON.stringify({ amount: 100 }),
    { headers: { 'Content-Type': 'application/json', 'Idempotency-Key': `v3-consistency-vu${__VU}-iter${__ITER}-${data.runId}` } }
  );
  if (res.status === 200) v3ConsistencySuccess.add(1);
  if (res.status === 409) v3Conflict.add(1);
  check(res, { '[v3-정합성] 허용된 응답': (r) => r.status === 200 || r.status === 400 || r.status === 409 });
}

export function v3IdempotencyTest(data) {
  const res = http.post(
    `${BASE}/api/v3/wallets/${data.v3IdempotencyId}/withdraw`,
    JSON.stringify({ amount: 100 }),
    { headers: { 'Content-Type': 'application/json', 'Idempotency-Key': `v3-idempotency-fixed-key-${data.runId}` } }
  );
  if (res.status === 200) v3IdempotencySuccess.add(1);
  check(res, { '[v3-멱등성] 허용된 응답': (r) => r.status === 200 || r.status === 400 || r.status === 409 });
}

export function v4Test(data) {
  const res = http.post(
    `${BASE}/api/v4/wallets/${data.v4Id}/withdraw`,
    JSON.stringify({ amount: 100 }),
    { headers: { 'Content-Type': 'application/json' } }
  );
  if (res.status === 200) v4Success.add(1);
  else if (res.status === 400) v4Insufficient.add(1);
  else if (res.status === 409) v4Conflict.add(1);
  check(res, { '[v4] 허용된 응답': (r) => r.status === 200 || r.status === 400 || r.status === 409 });
}

// ─── Teardown ─────────────────────────────────────────────────────────────────

export function teardown(data) {
  if (data.v1Id) {
    const body = JSON.parse(http.get(`${BASE}/api/wallets/${data.v1Id}`).body);
    console.log('\n========== [v1 비관적락] 정합성 검증 ==========');
    console.log(`최종 잔액: ${body.balance}원   기대: 0원 이상`);
    console.log(body.balance >= 0 ? `  ✓ PASS` : `  ✗ FAIL — 잔액 ${body.balance}원 (음수 → 정합성 깨짐)`);
    console.log('=================================================');
  }

  if (data.v2Id) {
    const body = JSON.parse(http.get(`${BASE}/api/wallets/${data.v2Id}`).body);
    console.log('\n========== [v2 낙관적락] 정합성 검증 ==========');
    console.log(`최종 잔액: ${body.balance}원   기대: 0원 이상`);
    console.log(body.balance >= 0 ? `  ✓ PASS` : `  ✗ FAIL — 잔액 ${body.balance}원 (음수 → 정합성 깨짐)`);
    console.log('=================================================');
  }

  if (data.v3ConsistencyId) {
    const balA = JSON.parse(http.get(`${BASE}/api/wallets/${data.v3ConsistencyId}`).body).balance;
    const balB = JSON.parse(http.get(`${BASE}/api/wallets/${data.v3IdempotencyId}`).body).balance;

    console.log('\n========== [v3 낙관적락] 정합성 테스트 ==========');
    console.log(`최종 잔액: ${balA}원   기대: 0원 이상`);
    console.log(balA >= 0 ? `  ✓ PASS` : `  ✗ FAIL — 잔액 ${balA}원 (음수)`);

    console.log('\n========== [v3 낙관적락] 멱등성 테스트 ==========');
    console.log(`최종 잔액: ${balB}원   기대: 4,900원 (1회만 출금)`);
    console.log(balB === 4900 ? '  ✓ PASS' : `  ✗ FAIL — 잔액 ${balB}원`);
    console.log('==================================================');
  }

  if (data.v4Id) {
    const body = JSON.parse(http.get(`${BASE}/api/wallets/${data.v4Id}`).body);
    console.log('\n========== [v4 Redis분산락] 정합성 검증 ==========');
    console.log(`최종 잔액: ${body.balance}원   기대: 0원 이상`);
    console.log(body.balance >= 0 ? `  ✓ PASS` : `  ✗ FAIL`);
    console.log('===================================================');
  }
}