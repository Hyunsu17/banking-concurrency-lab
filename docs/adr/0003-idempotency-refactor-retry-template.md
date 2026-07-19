# ADR-0003: v2.5 멱등성 리팩터링 — OptimisticRetryTemplate 분리 + Idempotency-Key 헤더

- **날짜**: 2026-06-28
- **상태**: 채택
- **관련**: [ADR-0001](0001-optimistic-lock-self-invocation.md), [ADR-0002](0002-idempotency-key-transfer-suffix.md)

## 컨텍스트

v2.5 구현 코드 리뷰 결과 두 가지 구조적 문제가 발견됐다.

1. `OptimisticTransactionService`와 `OptimisticIdempotentService`에 `withRetry` + `ThrowingSupplier`가 완전히 중복.
2. `deposit`/`withdraw`/`transfer` 세 메서드에 동일한 멱등성 체크 흐름이 반복.

그리고 컨트롤러 설계에서 Idempotency-Key를 어디에 실을지 결정이 필요했다.

## 검토한 선택지

### (1) OptimisticRetryTemplate 분리

- **A. 각 Service에 withRetry 중복 유지** — 변경 없이 간단하지만, 재시도 정책(횟수, 백오프) 변경 시 두 곳을 수정해야 해 동기화 누락 위험이 있다.
- **B. `@Component`로 추출** — 재시도 정책을 한 곳에서 관리. `@Spy`로 주입하면 단위 테스트에서 실제 retry 흐름을 검증할 수 있다.

### (2) executeIdempotently 추출

세 메서드 모두 동일 흐름(① 키 중복 체크 → ② 있으면 반환 → ③ 없으면 retryTemplate 실행 → ④ DIV 예외 시 반환)을 갖는다.

- **A. 각 메서드에 직접 작성** — 흐름 변경 시 3곳을 수정해야 한다.
- **B. `executeIdempotently()` private 메서드 추출** — 흐름 변경은 1곳, public 메서드는 2줄로 축소.

### (3) Idempotency-Key 위치

- **A. Request Body에 포함** — 비즈니스 페이로드와 요청 식별 메타데이터가 섞인다.
- **B. Request Header (`Idempotency-Key`)** — 비즈니스 페이로드(금액, 계좌)와 요청 식별(키)의 레이어를 분리한다. Stripe, 카카오페이 등 금융 API의 표준 방식이다.

## 결정

세 항목 모두 **B**를 선택했다.

```java
// OptimisticRetryTemplate — 재시도 정책 단일 소유
@Component
public class OptimisticRetryTemplate {
    public <T> T execute(ThrowingSupplier<T> action) throws InterruptedException {
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try { return action.get(); }
            catch (ObjectOptimisticLockingFailureException e) {
                if (attempt == MAX_RETRIES - 1) throw ...;  // 마지막엔 즉시 throw
                Thread.sleep((long)(Math.pow(2, attempt) * BASE_DELAY_MS));
            }
        }
    }
}

// executeIdempotently — 멱등성 체크 흐름 단일 소유
private List<Transaction> executeIdempotently(String lookupKey, Long walletId,
        ThrowingSupplier<List<Transaction>> action) throws InterruptedException {
    if (transactionRepository.findByIdempotencyKey(lookupKey).isPresent()) {
        return transactionRepository.findByWalletIdOrderByCreatedAtAsc(walletId);
    }
    try { return retryTemplate.execute(action); }
    catch (DataIntegrityViolationException e) {
        return transactionRepository.findByWalletIdOrderByCreatedAtAsc(walletId);
    }
}

// 컨트롤러 — Header 수신
@PostMapping("/{walletId}/withdraw")
public ResponseEntity<?> withdraw(
    @PathVariable Long walletId,
    @RequestBody WithdrawRequest req,
    @RequestHeader("Idempotency-Key") String idempotencyKey  // 누락 시 400 자동
) { ... }
```

## 근거

- **OptimisticRetryTemplate**: 재시도 횟수/백오프 정책을 한 곳에서 관리한다. `@Spy` 주입으로 단위 테스트에서 retry 흐름을 실제로 실행할 수 있다(`@Mock`은 action을 실행하지 않는다).
- **executeIdempotently**: 멱등성 흐름(조회 → 처리 → 예외 catch)이 변경될 때 한 곳만 수정하면 된다.
- **Request Header**: Idempotency-Key는 HTTP 요청 식별 메타데이터로, Body의 비즈니스 데이터와 관심사가 다르다. `@RequestHeader`는 누락 시 Spring이 자동으로 400을 응답한다.

## 부가 수정 — transfer 키 구분자

```java
// 기존: 구분자 없는 concat
idempotencyKey + TransactionType.TRANSFER_OUT  // → "abc123TRANSFER_OUT"

// 수정: 명시적 구분자 + .name()
idempotencyKey + ":" + TransactionType.TRANSFER_OUT.name()  // → "abc123:TRANSFER_OUT"
```

`idempotencyKey="abcTRANSFER_OUT"` 같은 키가 오면 suffix 없는 요청과 이론적으로 충돌할 수 있어 `:` 구분자로 방어했다.

## 결과

k6 v2.5 검증 결과:
- 정합성: 최종 잔액 음수 없음 ✅
- 멱등성: 50 VU 동일 키 → 100원 1번만 출금, 나머지 49건 모두 200 응답 ✅
