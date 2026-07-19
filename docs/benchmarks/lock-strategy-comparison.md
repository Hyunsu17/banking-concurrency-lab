# 락 전략 비교 — v0~v4 실측

## 측정 조건

- 환경: PostgreSQL 16 + Redis 7 (로컬, Docker Compose)
- k6: 100 VU, 200 iterations
- 초기 잔액 10,000원, 회당 출금 100원
- 재현: `make test` (전체) / `make test-v2`, `make test-v3`, `make test-v4` (개별) — [test.js](../../test.js)

## 비교표 (최종)

| 전략 | iterations/s | avg | p90 | p95 | 성공건 | 정합성 | 적합한 상황 |
|------|:-----------:|:---:|:---:|:---:|:------:|:------:|------------|
| v0 naive (락 없음) | ~240/s | 371ms | 575ms | 607ms | 200/200 | ❌ | 베이스라인 |
| v1 비관적 락 | ~210/s | 441ms | 805ms | 845ms | 100/200 | ✅ | 충돌 잦은 단일 DB |
| v2 낙관적 락 | ~234/s | 323ms | 469ms | 482ms | 81/200 | ✅ | 충돌 드문 읽기 위주 |
| v2.5 멱등성 키 | (별도 시나리오) | 408ms | 785ms | 828ms | - | ✅ | 네트워크 재시도 방어 |
| v4 Redis 분산락 | ~90/s | 973ms | 1,550ms | 1,620ms | 100/200 | ✅ | 다중 인스턴스 |

> v2.5는 35초 2-시나리오(정합성 + 멱등성) 혼합 측정이라 다른 전략과 iterations/s를 직접 비교할 수 없다.

## Race condition 재현 (v0)

| 항목 | 정상 | 실제 |
|------|------|------|
| 최종 잔액 | 0원 | **7,800원** |
| 성공 건수 | 100건 | **200건** |

출금 200번이 모두 성공 응답 — 실제 상황이었으면 대형 금융사고. 락 전략 비교의 베이스라인.

## 측정 이력 — 재측정으로 수치가 바뀐 것

두 전략 모두 최초 측정에 버그가 섞여 있었고, 수정 후 재측정한 값이 위 최종 비교표 기준이다.

### v1 비관적 락

| 항목 | 초기 측정 (sleep 버그 포함) | 최종 (재측정) |
|------|------|------|
| TPS | 181/s | **~210/s** |
| p95 | 1,010ms | **845ms** |

v0 대비 실제 TPS 손실은 -13% (초기 측정 기준 -25%보다 작다).

### v2 낙관적 락

마지막 attempt 실패 시 불필요한 400ms sleep이 있었다 (`119건 × 400ms = 47.6초` 낭비). 제거 후 재측정:

| 항목 | sleep 버그 | fix 후 재측정 | 개선 |
|------|-----------|------------|------|
| TPS | ~113.7/s | **~234/s** | +106% |
| p95 | 1,050ms | **482ms** | -54% |

v2 낙관적 락은 v1 비관적 락(~210/s) 대비 **~11% 높은 TPS**.

## 멱등성 (v2.5) 검증

- 정합성: 잔액 1,900원 (음수 없음) ✅
- 멱등성: 50 VU 동일 키 → 100원 1번만 출금, 나머지 49건 모두 200 응답 ✅

## 알려진 이슈 — v1 데드락 취약점 (미수정)

v1 `PessimisticTransactionService.transfer`는 from→to 순서로 DB lock을 획득한다.
A가 `1→2`, B가 `2→1`로 동시 송금하면 교차 락 획득으로 `PostgreSQL deadlock detected`가 발생할 수 있다.
k6가 단방향 송금만 테스트해서 미발견 상태였다. v4는 `Math.min/max` ID 정렬로 해결했다 ([ADR-0004](../adr/0004-redis-distributed-lock-design.md)).
v1에는 동일 패치가 아직 적용되지 않았다.

## 관련 ADR

- [ADR-0001](../adr/0001-optimistic-lock-self-invocation.md) — 낙관적 락 재시도 Service/Executor 분리
- [ADR-0002](../adr/0002-idempotency-key-transfer-suffix.md) — 멱등성 키 transfer suffix 설계
- [ADR-0003](../adr/0003-idempotency-refactor-retry-template.md) — v2.5 멱등성 리팩터링
- [ADR-0004](../adr/0004-redis-distributed-lock-design.md) — Redis 분산락 설계
