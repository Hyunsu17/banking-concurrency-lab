package com.khs.wallet.service;

import com.khs.wallet.domain.Transaction;
import com.khs.wallet.exception.LockAcquisitionException;
import com.khs.wallet.repository.TransactionRepository;
import com.khs.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class RedisTransactionService {

    private static final String LOCK_PREFIX = "wallet:lock:";
    private static final long WAIT_SECONDS = 3;
    private static final long LEASE_SECONDS = 5;

    private final RedissonClient redissonClient;
    private final OptimisticTransactionExecutor optimisticTransactionExecutor;
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;

    public List<Transaction> deposit(Long walletId, Long depositAmount) throws InterruptedException {
        RLock lock = acquireLock(walletId);
        try {
            return optimisticTransactionExecutor.deposit(walletId, depositAmount);
        } finally {
            lock.unlock();
        }
    }

    public List<Transaction> withdraw(Long walletId, Long withdrawAmount) throws InterruptedException {
        RLock lock = acquireLock(walletId);
        try {
            return optimisticTransactionExecutor.withdraw(walletId, withdrawAmount);
        } finally {
            lock.unlock();
        }
    }

    public List<Transaction> transfer(Long fromId, Long toId, Long amount) throws InterruptedException {
        RLock firstLock = acquireLock(Math.min(fromId, toId));
        try {
            RLock secondLock = acquireLock(Math.max(fromId, toId));
            try {
                return optimisticTransactionExecutor.transfer(fromId, toId, amount);
            } finally {
                secondLock.unlock();
            }
        } finally {
            firstLock.unlock();
        }
    }

    private RLock acquireLock(long walletId) throws InterruptedException {
        RLock lock = redissonClient.getLock(LOCK_PREFIX + walletId);
        if (!lock.tryLock(WAIT_SECONDS, LEASE_SECONDS, TimeUnit.SECONDS)) {
            throw new LockAcquisitionException("락 획득 실패");
        }
        return lock;
    }

    public List<Transaction> getTransactions(Long walletId) {
        walletRepository.findById(walletId)
                .orElseThrow(()-> new IllegalArgumentException("지갑을 찾을 수 없습니다"));
        return transactionRepository.findByWalletIdOrderByCreatedAtAsc(walletId);
    }
}
