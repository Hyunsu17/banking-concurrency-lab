# ADR-0001: 낙관적 락 재시도 — Service/Executor 2클래스 분리

- **날짜**: 2026-06-26
- **상태**: 채택

## 컨텍스트

v2 낙관적 락 구현 중, `OptimisticLockingFailureException` 발생 시 재시도가 동작하지 않는 버그를 발견했다.

재시도 루프(`deposit()`)와 실제 DB 작업(`depositInternal()`)을 같은 클래스 안에 구현하면:

```java
@Service
public class OptimisticTransactionService {

    @Transactional          // 외부 트랜잭션
    public List<Transaction> deposit(...) {
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                return depositInternal(...);  // self-invocation (Spring 프록시 우회)
            } catch (ObjectOptimisticLockingFailureException e) {
                // 재시도...
            }
        }
    }

    // @Transactional 없음 — self-invocation이므로 프록시 적용 안 됨
    private List<Transaction> depositInternal(...) { ... }
}
```

`depositInternal()`은 Spring 프록시를 거치지 않고 직접 호출(self-invocation)된다.
`ObjectOptimisticLockingFailureException`이 발생하면 외부 `@Transactional`이 rollback-only로 마킹되고,
재시도해도 이미 망가진 같은 트랜잭션 안에서 실행되어 재시도가 의미 없어진다.

## 검토한 선택지

**B. 내부 로직을 별도 `@Service`로 분리** — 재시도 루프(`OptimisticTransactionService`, `@Transactional` 없음)와
DB 작업(`OptimisticTransactionExecutor`, `@Transactional` 적용)을 분리해 프록시를 통해 호출.

## 결정

**B — Service/Executor 2클래스 분리**를 채택했다.

```java
// 재시도 루프 담당 — @Transactional 없음
@Service
public class OptimisticTransactionService {
    private final OptimisticTransactionExecutor executor;

    public List<Transaction> deposit(Long walletId, long amount) {
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                return executor.depositInternal(walletId, amount);  // 프록시 통해 호출
            } catch (ObjectOptimisticLockingFailureException e) {
                if (attempt == MAX_RETRIES - 1) throw e;
            }
        }
    }
}

// 실제 DB 작업 담당 — 독립 트랜잭션
@Service
public class OptimisticTransactionExecutor {

    @Transactional  // Spring 프록시 적용됨
    public List<Transaction> depositInternal(Long walletId, long amount) { ... }
}
```

## 근거

- 재시도 루프는 트랜잭션 외부에 있어야 한다. 각 시도가 독립적인 트랜잭션으로 시작/종료되어야 실패 시 해당 시도만 롤백되고 다음 시도가 새 트랜잭션으로 정상 실행된다.
- Spring AOP는 프록시 기반이라 같은 빈 내부 호출(self-invocation)은 프록시를 우회하므로 `@Transactional`이 적용되지 않는다.
- 재시도 정책(횟수, 예외, 백오프)과 실제 비즈니스 로직의 책임이 명확히 분리된다.

## 결과

단위 테스트(Mockito)로 재시도 로직을 독립적으로 검증할 수 있게 됐다.
통합 테스트(Testcontainers)로 정합성 검증: `6200 + 38×100 = 10,000 ✅`
