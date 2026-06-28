package com.khs.wallet.controller;

import com.khs.wallet.domain.TransactionType;

import java.time.LocalDateTime;

public record TransactionResponse(TransactionType type, Long Amount , Long balanceAfter, Long relatedWalletId, LocalDateTime createdAt) {
}
