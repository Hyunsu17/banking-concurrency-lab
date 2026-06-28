package com.khs.wallet.repository;

import com.khs.wallet.domain.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findByWalletIdOrderByCreatedAtAsc(Long walletId);

    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);
}
