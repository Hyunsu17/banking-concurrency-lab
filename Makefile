.PHONY: test test-v2 test-v3 test-v4 help

BASE_URL ?= http://localhost:8080

help:
	@echo "사용법: make <target>"
	@echo ""
	@echo "  make test        전체 순차 실행 (v2 → v3 → v4, 약 2.5분)"
	@echo "  make test-v2     v2 비관적락만"
	@echo "  make test-v3     v3 낙관적락 + 멱등성만"
	@echo "  make test-v4     v4 Redis 분산락만"

test:
	k6 run test.js -e BASE_URL=$(BASE_URL)

test-v2:
	k6 run test.js -e VERSION=v2 -e BASE_URL=$(BASE_URL)

test-v3:
	k6 run test.js -e VERSION=v3 -e BASE_URL=$(BASE_URL)

test-v4:
	k6 run test.js -e VERSION=v4 -e BASE_URL=$(BASE_URL)
