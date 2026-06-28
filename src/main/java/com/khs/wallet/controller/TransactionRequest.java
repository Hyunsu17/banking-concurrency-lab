package com.khs.wallet.controller;

public record TransactionRequest(Long fromWalletId, Long toWalletId, Long amount) {
}
