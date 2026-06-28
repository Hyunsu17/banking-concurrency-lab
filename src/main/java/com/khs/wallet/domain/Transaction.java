package com.khs.wallet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "transactions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "wallet_id", nullable = false)
    private Long walletId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionType type;

    @Column(name = "idempotency_key", unique = true, length = 100)
    private String idempotencyKey;

    @Column(nullable = false)
    private Long amount;

    @Column(name = "balance_after", nullable = false)
    private Long balanceAfter;

    @Column(name = "related_wallet_id")
    private Long relatedWalletId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public Transaction(Long walletId, TransactionType type, Long amount, Long balanceAfter, Long relatedWalletId, String idempotencyKey) {
        this.walletId = walletId;
        this.type = type;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.relatedWalletId = relatedWalletId;
        this.idempotencyKey = idempotencyKey;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
