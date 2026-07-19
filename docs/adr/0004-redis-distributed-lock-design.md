# ADR-0004: Redis 분산락 — acquireLock 헬퍼, LockAcquisitionException, InterruptedException 처리

- **날짜**: 2026-06-28
- **상태**: 채택
- **관련**: [ADR-0001](0001-optimistic-lock-self-invocation.md)

## 컨텍스트

v4 Redis 분산락 구현 후 코드 리뷰에서 3가지 설계 문제가 발견됐다.

1. `tryLock` 실패 처리 코드가 중첩 try 안에 끼어 있어 흐름 파악이 어렵다.
2. 락 실패와 잔액 부족이 모두 `IllegalStateException`이라 컨트롤러에서 HTTP 상태코드를 구분할 수 없다.
3. `InterruptedException`을 catch한 뒤 인터럽트 신호를 그대로 삼킨다.

## 검토한 선택지

### (1) acquireLock 헬퍼 추출 여부

- **A. tryLock + 실패 처리를 transfer 본문에 인라인** — 코드는 중복되지만 흐름이 선형적으로 보인다.
- **B. `acquireLock(walletId)` 헬퍼 추출** — transfer 본문에는 "첫 번째 락 → 두 번째 락 → executor 호출"이라는 핵심 구조만 남는다.

### (2) LockAcquisitionException 신설 여부

- **A. `IllegalStateException` 그대로 사용** — 잔액 부족과 락 실패를 구분할 수 없어 컨트롤러에서 400/409 분리가 어렵다.
- **B. `LockAcquisitionException extends RuntimeException` 신설** — `LockAcquisitionException`은 409 Conflict, `IllegalStateException`은 400 Bad Request로 분리한다(catch 순서는 구체적인 예외를 먼저 둔다).

### (3) InterruptedException 처리

- **A. catch 후 로그만 남기고 500 반환** — 인터럽트 신호를 삼켜 상위 레이어에서 스레드 종료를 감지할 수 없다.
- **B. `Thread.currentThread().interrupt()` 추가 후 반환** — 인터럽트 신호를 복원한다.

## 결정

세 항목 모두 **B**를 선택했다.

```java
// 최종 transfer 구조 — 낮은 ID 먼저 락 획득으로 데드락 방지
RLock firstLock = acquireLock(Math.min(fromId, toId));
try {
    RLock secondLock = acquireLock(Math.max(fromId, toId));
    try { return executor.transfer(fromId, toId, amount); }
    finally { secondLock.unlock(); }
} finally { firstLock.unlock(); }
```

```java
// 컨트롤러 catch 순서 (구체적인 것 먼저)
} catch (LockAcquisitionException e) {    // 409
} catch (IllegalStateException e) {        // 400
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    return ResponseEntity.internalServerError().build();
}
```

## 근거

- **acquireLock 헬퍼**: tryLock + 실패 throw가 3곳에서 동일하게 반복된다. 추출 후 transfer 본문은 "어떤 순서로 락을 잡는지"만 보인다.
- **LockAcquisitionException**: HTTP 상태코드 의미를 Service 레이어에서 컨트롤러로 전달하는 유일한 수단이 예외 타입이다. unchecked라 `throws` 선언 없이 자동 전파된다.
- **`Thread.currentThread().interrupt()`**: checked exception인 `InterruptedException`을 catch하면 인터럽트 플래그가 초기화된다. `interrupt()`로 복원하지 않으면 상위 코드(스레드 풀, 셧다운 훅)가 종료 신호를 놓칠 수 있다.

## 부가 학습 — acquireLock에서 finally가 불필요한 이유

```java
private RLock acquireLock(long walletId) throws InterruptedException {
    RLock lock = redissonClient.getLock(LOCK_PREFIX + walletId);
    if (!lock.tryLock(...)) throw ...;  // 락 획득 실패 → 락이 없으므로 unlock 대상 없음
    return lock;                         // 획득 성공 → 호출자의 try-finally가 책임짐
}
// finally 필요 없음
```

## 알려진 이슈 — v1 비관적 락 데드락 취약점 (미수정)

v1 `PessimisticTransactionService.transfer`는 from→to 순서로 DB lock을 획득한다.
A가 `1→2`, B가 `2→1`로 동시 송금하면 교차 락 획득으로 `PostgreSQL deadlock detected`가 발생할 수 있다.
k6가 단방향 송금만 테스트해서 미발견 상태였다. v4는 `Math.min/max` ID 정렬로 해결했지만, v1에는 동일 패치가
아직 적용되지 않았다.

## 결과

단위 테스트 7/7, 통합 테스트(Testcontainers) 4/4 모두 통과.
k6: 100건 정합성 성립, 잔액 0원 ✅ (~90/s, p95 1,620ms — 단일 인스턴스에서는 v1 비관적 락이 더 빠르다. Redis의 실제 가치는 앱 서버 N대 환경에서 나온다.)
