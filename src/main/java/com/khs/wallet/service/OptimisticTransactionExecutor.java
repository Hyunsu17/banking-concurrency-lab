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
public class OptimisticTransactionExecutor {

    private final TransactionRepository transactionRepository;
    private final WalletRepository walletRepository;

    @Transactional
    public List<Transaction> deposit(Long walletId, Long depositAmount) {
        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new IllegalArgumentException("해당지갑을 찾을 수 없습니다"));

        Long balanceAfter = wallet.getBalance() + depositAmount;

        transactionRepository.save(Transaction.builder()
                .walletId(walletId)
                .type(TransactionType.DEPOSIT)
                .amount(depositAmount)
                .balanceAfter(balanceAfter)
                .build());

        wallet.setBalance(balanceAfter);

        return transactionRepository.findByWalletIdOrderByCreatedAtAsc(walletId);
    }

    @Transactional
    public List<Transaction> withdraw(Long walletId, Long withdrawAmount) {
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
                .balanceAfter(balanceAfter)
                .build());

        wallet.setBalance(balanceAfter);

        return transactionRepository.findByWalletIdOrderByCreatedAtAsc(walletId);
    }

    @Transactional
    public List<Transaction> transfer(Long fromId, Long toId, Long amount) {
        if (fromId.equals(toId)) {
            throw new IllegalStateException("동일 지갑으로 송금할 수 없습니다");
        }

        Wallet fromWallet = walletRepository.findById(fromId)
                .orElseThrow(() -> new IllegalArgumentException("해당지갑을 찾을 수 없습니다"));
        Wallet toWallet = walletRepository.findById(toId)
                .orElseThrow(() -> new IllegalArgumentException("거래대상 지갑을 찾을 수 없습니다"));

        if (amount > fromWallet.getBalance()) {
            throw new IllegalStateException("잔액이 부족합니다");
        }

        Long fromBalanceAfter = fromWallet.getBalance() - amount;
        Long toBalanceAfter = toWallet.getBalance() + amount;

        transactionRepository.save(buildTransferTransaction(fromId, TransactionType.TRANSFER_OUT, amount, fromBalanceAfter, toId));
        transactionRepository.save(buildTransferTransaction(toId, TransactionType.TRANSFER_IN, amount, toBalanceAfter, fromId));

        fromWallet.setBalance(fromBalanceAfter);
        toWallet.setBalance(toBalanceAfter);

        return transactionRepository.findByWalletIdOrderByCreatedAtAsc(fromId);
    }

    private Transaction buildTransferTransaction(Long walletId, TransactionType type, Long amount, Long balanceAfter, Long relatedWalletId) {
        return Transaction.builder()
                .walletId(walletId)
                .type(type)
                .amount(amount)
                .balanceAfter(balanceAfter)
                .relatedWalletId(relatedWalletId)
                .build();
    }
}
