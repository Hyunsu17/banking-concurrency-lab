package com.khs.wallet.controller;

import com.khs.wallet.domain.Transaction;
import com.khs.wallet.service.OptimisticIdempotentService;
import com.khs.wallet.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v3")
@RequiredArgsConstructor
@Tag(name = "낙관적락 + 멱등성 트랜잭션 API")
public class V3TransactionController {

    private final OptimisticIdempotentService transactionService;
    private final WalletService walletService;

    @PostMapping("/wallets/{walletId}/deposit")
    @Operation(summary = "입금 거래 (멱등성 보장)")
    @ApiResponse(responseCode = "200", description = "입금 성공")
    @ApiResponse(responseCode = "400", description = "Idempotency-Key 헤더 누락")
    @ApiResponse(responseCode = "404", description = "지갑을 찾을 수 없음")
    public ResponseEntity<Map> deposit(
            @PathVariable Long walletId,
            @RequestBody TransactionRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        try {
            List<Transaction> transactionList = transactionService.deposit(walletId, request.amount(), idempotencyKey);
            Long balance = walletService.getWalletBalance(walletId);
            return ResponseEntity.ok(Map.of("balance", balance, "data", transactionList));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (InterruptedException e) {
            return ResponseEntity.internalServerError().build();
        } catch (ObjectOptimisticLockingFailureException e) {
            return ResponseEntity.status(409).build();
        }
    }

    @PostMapping("/wallets/{walletId}/withdraw")
    @Operation(summary = "출금 거래 (멱등성 보장)")
    @ApiResponse(responseCode = "200", description = "출금 성공")
    @ApiResponse(responseCode = "400", description = "잔액 부족 또는 Idempotency-Key 헤더 누락")
    @ApiResponse(responseCode = "404", description = "지갑을 찾을 수 없음")
    public ResponseEntity<Map> withdraw(
            @PathVariable Long walletId,
            @RequestBody TransactionRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        try {
            List<Transaction> transactionList = transactionService.withdraw(walletId, request.amount(), idempotencyKey);
            Long balance = walletService.getWalletBalance(walletId);
            return ResponseEntity.ok(Map.of("balance", balance, "data", transactionList));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        } catch (InterruptedException e) {
            return ResponseEntity.internalServerError().build();
        } catch (ObjectOptimisticLockingFailureException e) {
            return ResponseEntity.status(409).build();
        }
    }

    @PostMapping("/transfers")
    @Operation(summary = "이체 거래 (멱등성 보장)")
    @ApiResponse(responseCode = "200", description = "이체 성공")
    @ApiResponse(responseCode = "400", description = "잔액 부족 또는 Idempotency-Key 헤더 누락")
    @ApiResponse(responseCode = "404", description = "지갑을 찾을 수 없음")
    public ResponseEntity<Map> transfer(
            @RequestBody TransactionRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        try {
            List<Transaction> transactionList = transactionService.transfer(
                    request.fromWalletId(), request.toWalletId(), request.amount(), idempotencyKey);
            Long balance = walletService.getWalletBalance(request.fromWalletId());
            return ResponseEntity.ok(Map.of("balance", balance, "data", transactionList));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        } catch (InterruptedException e) {
            return ResponseEntity.internalServerError().build();
        } catch (ObjectOptimisticLockingFailureException e) {
            return ResponseEntity.status(409).build();
        }
    }

    @GetMapping("/wallets/{walletId}/transactions")
    @Operation(summary = "거래내역 조회")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "404", description = "지갑을 찾을 수 없음")
    public ResponseEntity<Map> getTransactions(@PathVariable Long walletId) {
        try {
            List<Transaction> transactionList = transactionService.getTransactions(walletId);
            Long balance = walletService.getWalletBalance(walletId);
            return ResponseEntity.ok(Map.of("balance", balance, "data", transactionList));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
