package com.khs.wallet.service;

import com.khs.wallet.domain.Transaction;
import com.khs.wallet.repository.TransactionRepository;
import com.khs.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OptimisticTransactionService {

    private final TransactionRepository transactionRepository;
    private final WalletRepository walletRepository;
    private final OptimisticTransactionExecutor executor;
    private final OptimisticRetryTemplate retryTemplate;

    public List<Transaction> deposit(Long walletId, Long depositAmount) throws InterruptedException {
        return retryTemplate.execute(() -> executor.deposit(walletId, depositAmount));
    }

    public List<Transaction> withdraw(Long walletId, Long withdrawAmount) throws InterruptedException {
        return retryTemplate.execute(() -> executor.withdraw(walletId, withdrawAmount));
    }

    public List<Transaction> transfer(Long fromId, Long toId, Long amount) throws InterruptedException {
        return retryTemplate.execute(() -> executor.transfer(fromId, toId, amount));
    }

    public List<Transaction> getTransactions(Long walletId) {
        walletRepository.findById(walletId)
                .orElseThrow(() -> new IllegalArgumentException("지갑을 찾을 수 없습니다"));
        return transactionRepository.findByWalletIdOrderByCreatedAtAsc(walletId);
    }
}
