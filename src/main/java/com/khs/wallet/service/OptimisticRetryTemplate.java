package com.khs.wallet.service;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

@Component
public class OptimisticRetryTemplate {

    private static final int MAX_RETRIES = 3;
    private static final int BASE_DELAY_MS = 100;

    public <T> T execute(ThrowingSupplier<T> action) throws InterruptedException {
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                return action.get();
            } catch (ObjectOptimisticLockingFailureException e) {
                if (attempt == MAX_RETRIES - 1) {
                    throw new ObjectOptimisticLockingFailureException("재시도 횟수 초과", e);
                }
                Thread.sleep((long) (Math.pow(2, attempt) * BASE_DELAY_MS));
            }
        }
        throw new IllegalStateException("unreachable");
    }

    @FunctionalInterface
    public interface ThrowingSupplier<T> {
        T get();
    }
}
