package com.khs.wallet.service;

import com.khs.wallet.domain.Transaction;
import com.khs.wallet.domain.TransactionType;
import com.khs.wallet.repository.TransactionRepository;
import com.khs.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OptimisticIdempotentService {

    private final TransactionRepository transactionRepository;
    private final WalletRepository walletRepository;
    private final OptimisticIdempotentExecutor executor;
    private final OptimisticRetryTemplate retryTemplate;

    public List<Transaction> deposit(Long walletId, Long depositAmount, String idempotencyKey) throws InterruptedException {
        return executeIdempotently(idempotencyKey, walletId,
                () -> executor.deposit(walletId, depositAmount, idempotencyKey));
    }

    public List<Transaction> withdraw(Long walletId, Long withdrawAmount, String idempotencyKey) throws InterruptedException {
        return executeIdempotently(idempotencyKey, walletId,
                () -> executor.withdraw(walletId, withdrawAmount, idempotencyKey));
    }

    public List<Transaction> transfer(Long fromId, Long toId, Long transferAmount, String idempotencyKey) throws InterruptedException {
        String transferOutKey = idempotencyKey + ":" + TransactionType.TRANSFER_OUT.name();
        return executeIdempotently(transferOutKey, fromId,
                () -> executor.transfer(fromId, toId, transferAmount, idempotencyKey));
    }

    private List<Transaction> executeIdempotently(String lookupKey, Long walletId,
            OptimisticRetryTemplate.ThrowingSupplier<List<Transaction>> action) throws InterruptedException {
        if (transactionRepository.findByIdempotencyKey(lookupKey).isPresent()) {
            return transactionRepository.findByWalletIdOrderByCreatedAtAsc(walletId);
        }
        try {
            return retryTemplate.execute(action);
        } catch (DataIntegrityViolationException e) {
            return transactionRepository.findByWalletIdOrderByCreatedAtAsc(walletId);
        }
    }

    public List<Transaction> getTransactions(Long walletId) {
        walletRepository.findById(walletId)
                .orElseThrow(() -> new IllegalArgumentException("지갑을 찾을 수 없습니다"));
        return transactionRepository.findByWalletIdOrderByCreatedAtAsc(walletId);
    }
}
