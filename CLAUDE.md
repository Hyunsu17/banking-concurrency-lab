# Wallet 프로젝트 — CLAUDE.md

## 프로젝트 개요

카카오뱅크 수신개발 면접 피드백 기반 포트폴리오 프로젝트.
동시성 락 전략(v0~v4)을 단계별로 구현하고 TPS/정합성을 실측 비교하는 **banking-concurrency-lab**.

> 한도요구불 수신 도메인은 별도 레포(`limit-demand/limit-demand-deposit`)로 분리됨 (2026-06-30).

**핵심 목표:**
- 동시성 제어 전략별 TPS/정합성 측정 (락 없음 → 비관적 락 → 낙관적 락 → Redis 분산락)
- 테스트 커버리지

---

## 기술 스택

| 분류 | 기술 |
|------|------|
| 언어 | Java 21 |
| 프레임워크 | Spring Boot 3.x |
| ORM | JPA (Spring Data JPA) |1
| DB | PostgreSQL 16 |
| 캐시/분산락 | Redis (Month 2~3 추가 예정) |
| 부하테스트 | k6 |
| 인프라 | Docker Compose |
| 모니터링 | Prometheus / Grafana (Month 4 추가 예정) |
| 테스트 | JUnit 5, Testcontainers |

---

## 로드맵

| 기간 | 목표 | 상태 |
|------|------|------|
| Month 1 | Race condition 재현 (naive 버전 + k6) | ✅ Week 01 완료 |
| Month 2 | 비관적 락 → 낙관적 락 → Redis 분산락, TPS/정합성 측정 | 진행 예정 |
| Month 3 | Docker, k8s, Prometheus/Grafana, CI/CD | 진행 예정 |
| Month 4 | RAG 백엔드 (pgvector + 평가 파이프라인) | 진행 예정 |
| Month 5 | 포트폴리오 패키징 + 지원 | 진행 예정 |

---

## 커리어 Wiki 참조

이 프로젝트의 커리어 맥락, 설계 문서, 의사결정 기록은 career-wiki를 참조한다.
Claude Code 세션 시작 시 아래 파일을 먼저 읽을 것:

- /mnt/c/dev/career-wiki/wiki/index.md
- /mnt/c/dev/career-wiki/wiki/projects/banking-concurrency-lab.md

---

## 행동 원칙 — Karpathy 4원칙

> 출처: `multica-ai/andrej-karpathy-skills` (220,000+ GitHub stars)
> Andrej Karpathy의 LLM 코딩 실패 패턴 관찰을 4가지 원칙으로 정리한 것

### 1. Think Before Coding — 추측 말고 질문
- 요청이 모호하면 멈추고 가정을 드러낸다
- 여러 해석이 가능하면 임의로 고르지 않고 전부 제시한다
- 더 단순한 접근이 있으면 먼저 말한다
- 불확실하면 코딩 전에 확인한다

### 2. Simplicity First — 요청한 것만 만든다
- 요청한 것만 구현한다. 추측성 기능 추가 금지
- 단일 사용 코드에 추상화 금지
- 불필요한 설정·옵션 추가 금지
- 50줄로 되면 200줄 쓰지 않는다
- self-check: "시니어 엔지니어가 보면 과하다고 할까?" → Yes면 다시

### 3. Surgical Changes — 요청한 곳만 건드린다
- 고쳐야 할 곳만 수정한다
- 관련 없는 dead code 발견 시 언급만, 삭제 금지
- 내 변경으로 생긴 unused import/변수/함수는 정리
- 기존 dead code는 요청 없으면 손대지 않는다
- self-check: "변경된 모든 라인이 요청에서 직접 비롯됐는가?"

### 4. Goal-Driven Execution — 성공 기준을 먼저 정의한다
- 코딩 전에 "완료"가 무엇인지 정의한다
- "고쳐줘" → "이 테스트를 통과시켜라"로 바꾼다
- 검증될 때까지 반복한다
- 강한 성공 기준이 있으면 독립적으로 루프 가능

> ⚠️ 이 원칙들은 속도보다 정확성을 우선한다. 단순 작업은 판단해서 적용.

---

## 코딩 규칙

### 일반
- 메서드 하나의 역할은 하나
- 매직 넘버 금지 — 상수 또는 Enum으로
- 주석은 "왜"를 설명 ("무엇"은 코드가 말함)
- 커밋 메시지: `[feat|fix|refactor|test|docs] 한 줄 설명`

### Spring / JPA
- Controller → Service → Repository 레이어 엄수
- 트랜잭션 경계는 Service에서만
- Entity에 비즈니스 로직 허용 (도메인 모델 패턴)
- `@Transactional(readOnly = true)` 조회 기본

### 동시성
- 락 전략 변경 시 반드시 `career-wiki/wiki/decisions/`에 ADR 기록
- 성능 측정은 k6 스크립트로 재현 가능하게 유지
- before/after 수치 비교 문서화 필수

### 테스트
- 핵심 비즈니스 로직은 단위 테스트 필수
- 동시성 테스트는 k6 + Testcontainers 병행
- 테스트 없는 PR 금지

---

## 의사결정 기록 방법

구현 중 기술 결정이 생기면:
1. `career-wiki/wiki/decisions/YYYY-MM-DD-결정제목.md` 생성
2. log.md에 `[DECISION]` 태그로 기록
3. 아래 기준을 충족하면 "주요 결정 이력"에 한 줄 추가

### 주요 결정 이력 기록 기준

> **"이 결정을 모르면 Claude가 잘못된 방향으로 코딩할 가능성이 있는가?"**

CLAUDE.md는 매 세션마다 로드되므로, Claude의 행동에 직접 영향을 주는 결정만 기록한다.

**기록해야 하는 것**
- 프로젝트 방향/범위를 바꾸는 결정 (도메인 추가, 기술 스택 교체 등)
- 패키지·테이블 구조 등 되돌리기 어려운 구조적 제약
- 모르면 Claude가 잘못된 위치에 코드를 생성하거나 잘못된 전략을 택하는 결정

**기록하지 않아도 되는 것** → `decisions/*.md` + `log.md`로 충분
- 구현 로드맵, 단계별 계획
- 측정 체계, k6 시나리오 설계
- 각 전략의 세부 구현 방식

### 주요 결정 이력
| 날짜 | 결정 | 링크 |
|------|------|------|
| 2026-06-22 | 프로젝트 시작 — banking-concurrency-lab (동시성 락 전략 v0~v4 비교) | - |
| 2026-06-30 | 한도요구불 도메인을 별도 레포(`limit-demand/limit-demand-deposit`)로 분리 — 이 레포는 동시성 락 비교에만 집중 | - |

---

## 폴더 구조 (목표)

```
src/
├── main/java/
│   └── com/wallet/
│       ├── domain/
│       │   └── wallet/          ← Wallet 도메인 (동시성 락 전략 비교)
│       ├── application/         ← Service 레이어
│       ├── infrastructure/      ← Repository, 외부 연동
│       └── interfaces/          ← Controller, DTO
└── test/
    ├── unit/
    ├── integration/
    └── load/                    ← k6 스크립트
```

---

## 작업 시작 전 체크리스트

새 기능 구현 시:
- [ ] career-wiki에서 관련 맥락 확인
- [ ] 테스트 시나리오 먼저 정의
- [ ] 락 전략이 달라지면 ADR 작성
