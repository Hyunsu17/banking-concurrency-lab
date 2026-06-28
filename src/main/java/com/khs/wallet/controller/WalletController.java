package com.khs.wallet.controller;

import com.khs.wallet.service.WalletService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/wallets")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    @GetMapping("/{walletId}")
    public ResponseEntity<WalletResponse> getWalletBalance(@PathVariable Long walletId){
        Long balance = walletService.getWalletBalance(walletId);
        return ResponseEntity.ok(new WalletResponse(walletId, balance));
    }



}
