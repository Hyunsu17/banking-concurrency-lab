package com.khs.wallet.controller;

import com.khs.wallet.domain.Transaction;
import com.khs.wallet.exception.LockAcquisitionException;
import com.khs.wallet.service.RedisTransactionService;
import com.khs.wallet.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v4")
@RequiredArgsConstructor
@Tag(name = "Redis 분산락 트랜잭션 API")
public class V4TransactionController {

    private final RedisTransactionService transactionService;
    private final WalletService walletService;

    @PostMapping("/wallets/{walletId}/deposit")
    @Operation(summary = "입금 거래")
    @ApiResponse(responseCode = "200", description = "입금 성공")
    @ApiResponse(responseCode = "404", description = "지갑을 찾을 수 없음")
    @ApiResponse(responseCode = "409", description = "락 획득 실패")
    public ResponseEntity<Map> deposit(@PathVariable Long walletId, @RequestBody TransactionRequest request) {
        try {
            List<Transaction> transactionList = transactionService.deposit(walletId, request.amount());
            return ResponseEntity.ok(Map.of("balance", walletService.getWalletBalance(walletId), "data", transactionList));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (LockAcquisitionException e) {
            return ResponseEntity.status(409).build();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/wallets/{walletId}/withdraw")
    @Operation(summary = "출금 거래")
    @ApiResponse(responseCode = "200", description = "출금 성공")
    @ApiResponse(responseCode = "400", description = "잔액 부족")
    @ApiResponse(responseCode = "404", description = "지갑을 찾을 수 없음")
    @ApiResponse(responseCode = "409", description = "락 획득 실패")
    public ResponseEntity<Map> withdraw(@PathVariable Long walletId, @RequestBody TransactionRequest request) {
        try {
            List<Transaction> transactionList = transactionService.withdraw(walletId, request.amount());
            return ResponseEntity.ok(Map.of("balance", walletService.getWalletBalance(walletId), "data", transactionList));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (LockAcquisitionException e) {
            return ResponseEntity.status(409).build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/transfers")
    @Operation(summary = "이체 거래")
    @ApiResponse(responseCode = "200", description = "이체 성공")
    @ApiResponse(responseCode = "400", description = "잔액 부족 또는 동일 지갑 송금")
    @ApiResponse(responseCode = "404", description = "지갑을 찾을 수 없음")
    @ApiResponse(responseCode = "409", description = "락 획득 실패")
    public ResponseEntity<Map> transfer(@RequestBody TransactionRequest request) {
        try {
            List<Transaction> transactionList = transactionService.transfer(request.fromWalletId(), request.toWalletId(), request.amount());
            return ResponseEntity.ok(Map.of("balance", walletService.getWalletBalance(request.fromWalletId()), "data", transactionList));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (LockAcquisitionException e) {
            return ResponseEntity.status(409).build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/wallets/{walletId}/transactions")
    @Operation(summary = "거래내역 조회")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "404", description = "지갑을 찾을 수 없음")
    public ResponseEntity<Map> getTransactions(@PathVariable Long walletId) {
        try {
            List<Transaction> transactionList = transactionService.getTransactions(walletId);
            return ResponseEntity.ok(Map.of("balance", walletService.getWalletBalance(walletId), "data", transactionList));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
