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
public class PessimisticTransactionService {

    private final TransactionRepository transactionRepository;

    private final WalletRepository walletRepository;

    @Transactional
    public List<Transaction> deposit(Long walletId, Long depositAmount) {

        //TODO Exception 처리 방식 고민
        // wallet 있는지 확인
        Wallet wallet = walletRepository.findByIdForUpdate(walletId).orElseThrow(() -> new IllegalArgumentException("해당지갑을 찾을 수 없습니다"));

        Long balanceAfter = wallet.getBalance() + depositAmount;

        //거래발생
        Transaction transaction = Transaction.builder()
                .walletId(walletId)
                .type(TransactionType.DEPOSIT)
                .amount(depositAmount)
                .balanceAfter(balanceAfter)
                .build();

        transactionRepository.save(transaction);

        //원장 수정
        wallet.setBalance(balanceAfter);

        return transactionRepository.findByWalletIdOrderByCreatedAtAsc(walletId);
    }

    @Transactional
    public List<Transaction> withdraw(Long walletId, Long withdrawAmount) {

        // wallet 있는지 확인
        Wallet wallet = walletRepository.findByIdForUpdate(walletId).orElseThrow(() -> new IllegalArgumentException("해당지갑을 찾을 수 없습니다"));

        //잔액이 인출요청금액보다 큰지 검증
        if (withdrawAmount > wallet.getBalance()) {
            throw new IllegalStateException("잔액이 부족합니다");
        }

        Long balanceAfter = wallet.getBalance() - withdrawAmount;
        // 거래 발생
        Transaction transaction = Transaction.builder()
                .walletId(walletId)
                .type(TransactionType.WITHDRAW)
                .amount(withdrawAmount)
                .balanceAfter(balanceAfter)
                .build();

        transactionRepository.save(transaction);

        // 원장 수정
        wallet.setBalance(balanceAfter);

        return transactionRepository.findByWalletIdOrderByCreatedAtAsc(walletId);
    }

    @Transactional
    public List<Transaction> transfer(Long fromId, Long toId, Long amount) {

        //TODO Exception 처리 방식 고민
        // wallet 있는지 확인
        Wallet fromWallet = walletRepository.findByIdForUpdate(fromId).orElseThrow(() -> new IllegalArgumentException("해당지갑을 찾을 수 없습니다"));

        Wallet toWallet = walletRepository.findByIdForUpdate(toId).orElseThrow(() -> new IllegalArgumentException("거래대상 지갑을 찾을 수 없습니다"));

        //잔액이 이체요청금액보다 큰지 검증
        if (amount > fromWallet.getBalance()) {
            throw new IllegalStateException("잔액이 부족합니다");
        }

        if (fromId.equals(toId)) {
            throw new IllegalStateException("동일 지갑으로 송금할 수 없습니다");
        }

        Long fromBalanceAfter = fromWallet.getBalance() - amount;
        Long toBalanceAfter = toWallet.getBalance() + amount;


        // 거래 발생(원천 지갑)
        Transaction fromTransaction = Transaction.builder()
                .walletId(fromId)
                .type(TransactionType.TRANSFER_OUT)
                .amount(amount)
                .balanceAfter(fromBalanceAfter)
                .relatedWalletId(toId)
                .build();

        // 거래 발생(대상 지갑)
        Transaction toTransaction = Transaction.builder()
                .walletId(toId)
                .type(TransactionType.TRANSFER_IN)
                .amount(amount)
                .balanceAfter(toBalanceAfter)
                .relatedWalletId(fromId)
                .build();

        transactionRepository.save(fromTransaction);
        transactionRepository.save(toTransaction);

        // 원장 수정
        fromWallet.setBalance(fromBalanceAfter);
        toWallet.setBalance(toBalanceAfter);

        // 원천계좌의 거래내역 반환
        return transactionRepository.findByWalletIdOrderByCreatedAtAsc(fromId);
    }

    public List<Transaction> getTransactions(Long walletId) {

        //TODO Exception 처리 방식 고민
        // wallet 있는지 확인
        walletRepository.findById(walletId).orElseThrow(() -> new IllegalArgumentException("지갑을 찾을 수 없습니다"));

        // 원천계좌의 거래내역 반환
        return transactionRepository.findByWalletIdOrderByCreatedAtAsc(walletId);
    }

}
