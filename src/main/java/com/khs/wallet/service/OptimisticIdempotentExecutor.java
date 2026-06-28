package com.khs.wallet.service;

import com.khs.wallet.domain.Transaction;
import com.khs.wallet.domain.TransactionType;
import com.khs.wallet.domain.Wallet;
import com.khs.wallet.repository.TransactionRepository;
import com.khs.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OptimisticIdempotentExecutor {

    private final TransactionRepository transactionRepository;
    private final WalletRepository walletRepository;

    @Transactional
    public List<Transaction> deposit(Long walletId, Long depositAmount, String idempotencyKey) {
        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new IllegalArgumentException("해당지갑을 찾을 수 없습니다"));

        Long balanceAfter = wallet.getBalance() + depositAmount;

        transactionRepository.save(Transaction.builder()
                .walletId(walletId)
                .type(TransactionType.DEPOSIT)
                .amount(depositAmount)
                .idempotencyKey(idempotencyKey)
                .balanceAfter(balanceAfter)
                .build());

        wallet.setBalance(balanceAfter);

        return transactionRepository.findByWalletIdOrderByCreatedAtAsc(walletId);
    }

    @Transactional
    public List<Transaction> withdraw(Long walletId, Long withdrawAmount, String idempotencyKey) {
        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new IllegalArgumentException("해당지갑을 찾을 수 없습니다"));

        if (withdrawAmount > wallet.getBalance()) {
            throw new IllegalStateException("잔액이 부족합니다");
        }

        Long balanceAfter = wallet.getBalance() - withdrawAmount;

        transactionRepository.save(Transaction.builder()
                .walletId(walletId)
                .type(TransactionType.WITHDRAW)
                .amount(withdrawAmount)
                .idempotencyKey(idempotencyKey)
                .balanceAfter(balanceAfter)
                .build());

        wallet.setBalance(balanceAfter);

        return transactionRepository.findByWalletIdOrderByCreatedAtAsc(walletId);
    }

    @Transactional
    public List<Transaction> transfer(Long fromId, Long toId, Long amount, String idempotencyKey) {

        Wallet fromWallet = walletRepository.findById(fromId)
                .orElseThrow(() -> new IllegalArgumentException("해당지갑을 찾을 수 없습니다"));
        Wallet toWallet = walletRepository.findById(toId)
                .orElseThrow(() -> new IllegalArgumentException("거래대상 지갑을 찾을 수 없습니다"));

        if (amount > fromWallet.getBalance()) {
            throw new IllegalStateException("잔액이 부족합니다");
        }
        if (fromId.equals(toId)) {
            throw new IllegalStateException("동일 지갑으로 송금할 수 없습니다");
        }

        Long fromBalanceAfter = fromWallet.getBalance() - amount;
        Long toBalanceAfter = toWallet.getBalance() + amount;

        transactionRepository.save(Transaction.builder()
                .walletId(fromId)
                .type(TransactionType.TRANSFER_OUT)
                .amount(amount)
                .balanceAfter(fromBalanceAfter)
                .idempotencyKey(idempotencyKey + ":" + TransactionType.TRANSFER_OUT.name())
                .relatedWalletId(toId)
                .build());

        transactionRepository.save(Transaction.builder()
                .walletId(toId)
                .type(TransactionType.TRANSFER_IN)
                .amount(amount)
                .balanceAfter(toBalanceAfter)
                .idempotencyKey(idempotencyKey + ":" + TransactionType.TRANSFER_IN.name())
                .relatedWalletId(fromId)
                .build());

        fromWallet.setBalance(fromBalanceAfter);
        toWallet.setBalance(toBalanceAfter);

        return transactionRepository.findByWalletIdOrderByCreatedAtAsc(fromId);
    }
}
