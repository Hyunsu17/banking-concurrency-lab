#!/usr/bin/env bash
# v4 부하를 거는 동안 실제로 잡힌 Redisson 락의 Hash 내부를 떠본다.
#
#   터미널 1:  ./scripts/peek-lock.sh          # 락이 잡힐 때까지 폴링
#   터미널 2:  make test-v4
#
# 락은 수 ms만 유지되므로 컨테이너 "안에서" 폴링해야 한다 (docker exec 왕복은 너무 느리다).
set -euo pipefail

CONTAINER=${CONTAINER:-wallet-redis}
TRIES=${TRIES:-200000}

docker cp "$(dirname "$0")/peek-lock.lua" "$CONTAINER:/tmp/peek-lock.lua" >/dev/null

echo "[peek] 락 획득을 기다리는 중... (다른 터미널에서 make test-v4 실행)"
docker exec "$CONTAINER" sh -c "
  for i in \$(seq 1 $TRIES); do
    out=\$(redis-cli --raw --eval /tmp/peek-lock.lua 0 2>/dev/null)
    if [ -n \"\$out\" ]; then
      echo \"--- 락 포착 (시도 \$i회) ---\"
      echo \"\$out\"
      exit 0
    fi
  done
  echo '[peek] 락을 못 잡았다 — 부하가 이미 끝났거나 시작 전이다.'
  exit 1
"
