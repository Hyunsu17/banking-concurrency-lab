# Wallet — 동시성 락 전략 비교 프로젝트

금융 도메인(수신)의 동시성 제어 문제를 실측 기반으로 비교하는 포트폴리오 프로젝트.  
`락 없음 → 비관적 락 → 낙관적 락 → 멱등성 키 → Redis 분산락` 순으로 전략을 적용하며 TPS / 정합성을 측정한다.

---

## 핵심 목표

- 동시성 전략별 **TPS / 정합성 실측** (k6 부하테스트)

---

## 기술 스택

| 분류 | 기술 |
|------|------|
| Language | Java 21 |
| Framework | Spring Boot 3.x |
| ORM | JPA (Spring Data JPA) |
| DB | PostgreSQL 16 |
| 캐시 / 분산락 | Redis 7 (Redisson) |
| 부하테스트 | k6 |
| 인프라 | Docker Compose |
| 테스트 | JUnit 5, Mockito, Testcontainers |

---

## 락 전략별 실측 결과

> 측정 조건: 100 VU · 200 iterations · 초기 잔액 10,000원 · 회당 출금 100원  
> 환경: PostgreSQL 16 + Redis 7 로컬

| 전략 | iterations/s | avg | p90 | p95 | 성공건 | 정합성 |
|------|:-----------:|:---:|:---:|:---:|:------:|:------:|
| v0 naive (락 없음) | ~240/s | - | - | - | 200/200 | ❌ 잔액 음수 |
| v1 비관적 락 | ~210/s | 441ms | 805ms | 845ms | 100/200 | ✅ |
| v2 낙관적 락 | ~234/s | 323ms | 469ms | 482ms | 81/200 | ✅ |
| v2.5 낙관적 락 + 멱등성 키 | - | - | 785ms | 828ms | - | ✅ 이중차감 없음 |
| v4 Redis 분산락 | ~90/s | 973ms | 1,550ms | 1,620ms | 100/200 | ✅ |

### Trade-off 요약

- **v1 비관적 락**: DB row lock으로 직렬화. 단순하지만 락 대기로 TPS 저하. 단일 인스턴스에 적합.
- **v2 낙관적 락**: 충돌 시 즉시 409 반환. p90이 가장 낮지만 고충돌 환경에서 미처리 건 발생.
- **v2.5 멱등성 키**: 낙관적 락 위에 DB UNIQUE 제약으로 네트워크 재시도(중복 클릭) 방어.
- **v4 Redis 분산락**: Redis에서 락 관리 → 앱 서버 N대에서도 정합성 보장. 단일 인스턴스에서는 Redis 왕복 오버헤드로 가장 느림.

---

## API 엔드포인트

| 버전 | 경로 | 전략 |
|------|------|------|
| v0 | `/api/wallets/{id}/withdraw` | 락 없음 (race condition 재현) |
| v1 | `/api/v1/wallets/{id}/withdraw` | 비관적 락 (`SELECT FOR UPDATE`) |
| v2 | `/api/v2/wallets/{id}/withdraw` | 낙관적 락 (`@Version` + 재시도) |
| v2.5 | `/api/v3/wallets/{id}/withdraw` | 낙관적 락 + 멱등성 키 (Idempotency-Key 헤더) |
| v4 | `/api/v4/wallets/{id}/withdraw` | Redis 분산락 (Redisson RLock) |

Swagger UI: `http://localhost:8080/swagger-ui/index.html`

---

## 실행 방법

### 1. 인프라 기동

```bash
docker compose up -d
```

PostgreSQL 16 (`:5432`) + Redis 7 (`:6379`) 컨테이너가 함께 기동된다.

### 2. 앱 실행

```bash
./gradlew bootRun
```

### 3. k6 부하테스트

지갑 생성·초기 입금은 `test.js`의 `setup()`이 자동으로 처리한다.

```bash
make test        # 전체 순차 실행 (v2 → v3 → v4, 약 2.5분)
make test-v2     # v2 비관적락만
make test-v3     # v3 낙관적락 + 멱등성만
make test-v4     # v4 Redis 분산락만

# make 없을 경우 (make 설치: sudo apt-get install -y make)
k6 run test.js
k6 run test.js -e VERSION=v2
```

---

## 프로젝트 구조

```
src/
├── main/java/com/khs/wallet/
│   ├── controller/
│   │   ├── V1TransactionController.java   # 비관적 락
│   │   ├── V2TransactionController.java   # 낙관적 락
│   │   ├── V3TransactionController.java   # 낙관적 락 + 멱등성 키
│   │   └── V4TransactionController.java   # Redis 분산락
│   ├── service/
│   │   ├── PessimisticTransactionService.java
│   │   ├── OptimisticTransactionService.java
│   │   ├── OptimisticTransactionExecutor.java
│   │   ├── OptimisticRetryTemplate.java
│   │   ├── OptimisticIdempotentService.java
│   │   ├── OptimisticIdempotentExecutor.java
│   │   └── RedisTransactionService.java
│   ├── domain/
│   │   ├── Wallet.java
│   │   └── Transaction.java
│   └── exception/
│       └── LockAcquisitionException.java
└── test/java/com/khs/wallet/
    └── service/
        ├── OptimisticConcurrencyTest.java     # Testcontainers 통합 테스트
        ├── OptimisticTransactionServiceTest.java
        ├── OptimisticIdempotentServiceTest.java
        └── RedisTransactionServiceTest.java

test.js                     # 전체 통합 k6 테스트 (VERSION=v2|v3|v4 로 개별 실행 가능)
Makefile                    # make test / test-v2 / test-v3 / test-v4
```

---

## 로드맵

| 기간 | 목표 | 상태 |
|------|------|:----:|
| Month 1 | Race condition 재현 (naive + k6) | ✅ |
| Month 2 | 비관적 락 → 낙관적 락 → 멱등성 키 → Redis 분산락 TPS 비교 | ✅ |
| Month 3 | 한도요구불 도메인 → 별도 프로젝트로 분리 | ↗ 분리 |
| Month 4 | Docker, k8s, Prometheus / Grafana, CI/CD | 🔜 |
| Month 5 | RAG 백엔드 (pgvector + 평가 파이프라인) | 🔜 |
| Month 6 | 포트폴리오 패키징 + 지원 | 🔜 |
