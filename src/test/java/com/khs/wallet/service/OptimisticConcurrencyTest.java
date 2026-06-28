package com.khs.wallet.service;

import com.khs.wallet.domain.Wallet;
import com.khs.wallet.repository.WalletRepository;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class OptimisticConcurrencyTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("wallet_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () ->redis.getMappedPort(6379));
    }

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7"))
            .withExposedPorts(6379);

    @Autowired
    private OptimisticTransactionService service;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private OptimisticIdempotentService idempotentService;

    @Autowired
    private RedisTransactionService redisTransactionService;

    /**
     * v2 낙관적 락 정합성 검증
     * 조건: 초기잔액 10,000원, 100 VU, 각 100원 출금
     * 기대: 잔액 + 성공건수×100 = 10,000 (유실/이중차감 없음)
     */
    @Test
    void 동시_출금_정합성_검증() throws InterruptedException {
        long initialBalance = 10_000L;
        long withdrawAmount = 100L;
        int threadCount = 100;

        Wallet wallet = walletRepository.save(Wallet.builder().userId(1L).balance(initialBalance).build());
        Long walletId = wallet.getId();

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    service.withdraw(walletId, withdrawAmount);
                    successCount.incrementAndGet();
                } catch (Exception ignored) {
                    // 잔액 부족 또는 재시도 초과 → 실패 허용
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        start.countDown();
        done.await();
        executorService.shutdown();

        Wallet after = walletRepository.findById(walletId).orElseThrow();

        System.out.printf("[낙관적 락] 성공: %d건, 최종잔액: %d원%n", successCount.get(), after.getBalance());

        // 핵심 정합성 검증: 잔액 + 성공총액 = 초기잔액 (유실/이중차감 없음)
        assertThat(after.getBalance() + (long) successCount.get() * withdrawAmount)
                .isEqualTo(initialBalance);

        // 잔액은 음수 불가
        assertThat(after.getBalance()).isGreaterThanOrEqualTo(0L);
    }

    @SneakyThrows
    @Test
    void 동일_키로_두번_요청시_한번만_차감() {
        // given
        Wallet wallet = walletRepository.save(
                Wallet.builder().userId(1L).balance(10_000L).build()
        );
        String key = UUID.randomUUID().toString();

        // when
        idempotentService.withdraw(wallet.getId(), 100L, key);
        idempotentService.withdraw(wallet.getId(), 100L, key); // 동일 키 재요청

        // then
        Wallet after = walletRepository.findById(wallet.getId()).orElseThrow();
        assertThat(after.getBalance()).isEqualTo(9_900L); // 한 번만 차감
    }

    @SneakyThrows
    @Test
    void 다른_키로_두번_요청시_각각_차감() {
        // given
        Wallet wallet = walletRepository.save(
                Wallet.builder().userId(1L).balance(10_000L).build()
        );

        //when
        idempotentService.withdraw(wallet.getId(), 100L, UUID.randomUUID().toString());
        idempotentService.withdraw(wallet.getId(), 100L, UUID.randomUUID().toString());

        //then
        Wallet after = walletRepository.findById(wallet.getId()).orElseThrow();
        assertThat(after.getBalance()).isEqualTo(9_800L); // 두번차감
    }


    /**
     * v4 REDIS 분산락 정합성 검증
     * 조건: 초기잔액 10,000원, 100 VU, 각 100원 출금
     * 기대: 잔액 + 성공건수×100 = 10,000 (유실/이중차감 없음)
     */
    @Test
    void 동시_출금_정합성_검증_REDIS() throws InterruptedException {
        long initialBalance = 10_000L;
        long withdrawAmount = 100L;
        int threadCount = 100;

        Wallet wallet = walletRepository.save(Wallet.builder().userId(1L).balance(initialBalance).build());
        Long walletId = wallet.getId();

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    redisTransactionService.withdraw(walletId, withdrawAmount);
                    successCount.incrementAndGet();
                } catch (Exception ignored) {
                    // 잔액 부족 또는 재시도 초과 → 실패 허용
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        start.countDown();
        done.await();
        executorService.shutdown();

        Wallet after = walletRepository.findById(walletId).orElseThrow();

        System.out.printf("[REDIS 분산 락] 성공: %d건, 최종잔액: %d원%n", successCount.get(), after.getBalance());

        // 핵심 정합성 검증: 잔액 + 성공총액 = 초기잔액 (유실/이중차감 없음)
        assertThat(after.getBalance() + (long) successCount.get() * withdrawAmount)
                .isEqualTo(initialBalance);

        // 잔액은 음수 불가
        assertThat(after.getBalance()).isGreaterThanOrEqualTo(0L);
    }

}
