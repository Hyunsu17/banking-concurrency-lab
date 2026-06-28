package com.khs.wallet.service;

import com.khs.wallet.domain.Wallet;
import com.khs.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;

    public Wallet createWallet(Long userId) {
        Wallet wallet = Wallet.builder()
                .userId(userId)
                .balance(0L)
                .build();

        return walletRepository.save(wallet);
    }

    public Long getWalletBalance(Long walletId){
        return walletRepository.findById(walletId).orElseThrow(() -> new IllegalArgumentException()).getBalance();
    }
}
