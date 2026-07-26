# 원본 측정 자료

문서에 실린 수치의 근거가 되는 원본 로그. 가공하지 않은 상태로 둔다.

## `2026-07-26-redis-monitor-v4.txt`

v4(Redis 분산락) 부하테스트 중 Redis가 처리한 모든 명령의 기록. 4,093줄.

**캡처 방법**

```bash
# 터미널 1
docker exec wallet-redis redis-cli MONITOR > monitor.txt
# 터미널 2
make test-v4
```

`MONITOR`는 Redis 서버 명령으로, 이 명령을 받은 연결에 서버가 처리하는 모든 명령을 인자까지 흘려보낸다.
서버가 처리량을 희생하며 명령을 복제하므로 **성능 측정과 같은 실행에서 쓰면 안 된다.**
[lock-strategy-comparison.md](../lock-strategy-comparison.md)의 TPS/p95는 MONITOR 없이 잰 별도 실행 값이다.

**한 줄의 구조**

```
1785032590.647476 [0 lua] "hincrby" "wallet:lock:36" "f3b002ec-...:103" "1"
└──── $1 ────┘ └─$2 $3─┘ └─ $4 ─┘ └───── $5 ─────┘ └───── $6 ─────┘ └$7┘
     시각        DB+출처     명령        인자1              인자2        인자3
```

`[0 lua]`는 Lua 스크립트가 서버 내부에서 실행한 명령, `[0 172.20.0.1:*]`은 클라이언트가 보낸 명령이다.

## 읽는 법

```bash
L=docs/benchmarks/raw/2026-07-26-redis-monitor-v4.txt

# 명령별 횟수 — 201(획득) + 397(튕김) = 598(시도)
#   201 = 출금 200건 + k6 setup()의 초기 예치 1건 (v4는 deposit도 같은 락을 거친다)
awk '{for(i=1;i<=NF;i++) if($i ~ /^"[A-Za-z]+"$/){print $i; break}}' $L | sort | uniq -c | sort -rn

# 락 하나의 생애주기 (EVAL 줄에 스크립트 전문이 있으므로 가로를 자른다)
sed -n '27,38p' $L | cut -c1-160

# 경합 장면 — 시각을 상대값으로 바꿔 획득/튕김/해제를 나열
grep -E '\[0 lua\] "(hincrby|pttl|del)"' $L | head -16 \
 | awk '{t=$1+0; if(base==0) base=t; cmd=$4; amt=$7;
         gsub(/"/,"",cmd); gsub(/"/,"",amt);
         label = (cmd=="pttl") ? "대기(튕김)" : (cmd=="del") ? "키 삭제" : (amt=="1") ? "획득" : "해제(-1)";
         printf "+%6.3fs  %-9s %s\n", t-base, cmd, label}'

# 락 소유자의 두 축 — field 100개(스레드)가 UUID 1개(인스턴스)로 접힌다
grep -oE "[0-9a-f-]{36}:[0-9]+" $L | sort -u | wc -l
grep -oE "[0-9a-f-]{36}:[0-9]+" $L | cut -d: -f1 | sort -u
```

## 락이 잡힌 순간의 키 상태

MONITOR가 오간 명령을 보여준다면, 이쪽은 특정 시점의 키 자체를 본다.
락은 수 ms만 유지되므로 컨테이너 안에서 폴링하고, `KEYS` → `HGETALL` 사이에 락이 풀리는 것을 막기 위해
`EVAL`로 한 번에 조회한다. [`scripts/peek-lock.sh`](../../../scripts/peek-lock.sh) 참고.

```
KEY   = wallet:lock:37     TYPE = hash        ENC  = listpack
PTTL  = 4999 ms            HLEN = 1
FIELD = f3b002ec-2f61-48b6-90bb-9c2e2410777c:199  ->  VALUE = 1
```

`HLEN`은 소유자 수다. 1이 아니면 상호 배제가 깨진 것이다.
