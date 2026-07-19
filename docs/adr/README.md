# ADR — Architecture Decision Records

v0~v4 락 전략 구현 과정에서 내린 설계 결정 기록.

| ID | 제목 | 날짜 |
|----|------|------|
| [0001](0001-optimistic-lock-self-invocation.md) | 낙관적 락 재시도 — Service/Executor 2클래스 분리 | 2026-06-26 |
| [0002](0002-idempotency-key-transfer-suffix.md) | 멱등성 키 — transfer suffix 설계 | 2026-06-27 |
| [0003](0003-idempotency-refactor-retry-template.md) | v2.5 멱등성 리팩터링 — RetryTemplate 분리 + Idempotency-Key 헤더 | 2026-06-28 |
| [0004](0004-redis-distributed-lock-design.md) | Redis 분산락 — acquireLock 헬퍼, LockAcquisitionException | 2026-06-28 |
