# ADR-0002: 멱등성 키 — transfer suffix 설계

- **날짜**: 2026-06-27
- **상태**: 채택

## 컨텍스트

v2.5 멱등성 키 구현 중, `transfer`(이체)는 `TRANSFER_OUT` / `TRANSFER_IN` 두 개의 거래가 생성된다.
멱등성 키 하나로 두 거래를 어떻게 추적할지 설계가 필요했다.

## 검토한 선택지

**A. OUT에만 키 저장** — 구현은 단순하지만 IN이 실제 처리됐는지 알 방법이 없다.
중간 장애(OUT 처리 완료, IN 처리 전 서버 다운)를 탐지할 수 없다.

**B. `{key}:TRANSFER_OUT` / `{key}:TRANSFER_IN` suffix 분리 저장** — IN/OUT을 각각 독립된 멱등성 키로 관리한다.
OUT은 있는데 IN이 없으면 배치로 미완료 transfer를 탐지할 수 있다. suffix는 `TransactionType` ENUM을 사용해
타입 변경 시 자동으로 따라가게 한다.

`withdraw`/`deposit`은 거래가 1개뿐이므로 suffix 없이 키를 그대로 저장한다.

## 결정

**B — ENUM suffix 분리 저장**을 채택했다.

```java
// transfer 멱등성 키
String outKey = idempotencyKey + ":" + TransactionType.TRANSFER_OUT.name();
String inKey  = idempotencyKey + ":" + TransactionType.TRANSFER_IN.name();

// withdraw/deposit 멱등성 키
String key = idempotencyKey;  // suffix 불필요
```

## 근거

- **장애 탐지 가능성**: OUT-IN 쌍이 분리되어 있어야 배치 스캔 시 "OUT만 있고 IN 없음 = 미완료 이체"를 식별할 수 있다.
- **타입 안전성**: 하드코딩 문자열 대신 ENUM을 사용해 `TransactionType` 값이 변경되면 suffix도 자동 반영된다.
- **컬럼 길이**: UUID(36자) + `:TRANSFER_OUT`(13자) = 49자 → `VARCHAR(100)`으로 여유를 확보한다.

## 부가 결정 — DataIntegrityViolationException 처리 위치

DB UNIQUE 제약 위반(`DataIntegrityViolationException`)을 Controller가 직접 처리하면 레이어 역할을 위반한다.
Service에서 catch해 기존 거래를 재조회 후 반환한다.

```java
try {
    return transactionRepository.save(transaction);
} catch (DataIntegrityViolationException e) {
    // idempotency_key UNIQUE 충돌 → 이미 처리된 거래 반환
    return transactionRepository.findByIdempotencyKey(idempotencyKey).orElseThrow();
}
```

> `DataIntegrityViolationException`은 UNIQUE 외 NOT NULL, FK, CHECK 위반도 동일 예외로 발생한다.
> 이 프로젝트에서는 변동 포인트가 `idempotency_key`뿐이라 그대로 catch한다.

## 결과

동시 중복 요청 흐름:

```
A, B 동시 도착 → 둘 다 SELECT → "없음"
→ A INSERT 성공
→ B INSERT → UNIQUE 위반 → catch → 기존 거래 재조회 반환 (멱등성 보장)
```
