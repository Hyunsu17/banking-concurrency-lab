package com.khs.wallet.controller;

import com.khs.wallet.domain.Transaction;
import com.khs.wallet.service.TransactionService;
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
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "트랜잭션 API")
public class TransactionController {

    private final TransactionService transactionService;
    private final WalletService walletService;

    @PostMapping("/wallets/{walletId}/deposit")
    @Operation(summary = "입금 거래")
    @ApiResponse(responseCode = "200", description = "입금 성공")
    @ApiResponse(responseCode = "404", description = "지갑을 찾을 수 없음") // 추가
    public ResponseEntity<Map> deposit(@PathVariable Long walletId, @RequestBody TransactionRequest request){

        Long depositAmount = request.amount();
        List<Transaction> transactionList;

        try {
            transactionList = transactionService.deposit(walletId, depositAmount);
        }catch (IllegalArgumentException iae){
            return ResponseEntity.notFound().build();
        }

        Long balance = walletService.getWalletBalance(walletId);


        return ResponseEntity.ok(Map.of("balance", balance, "data", transactionList));
    }

    @PostMapping("/wallets/{walletId}/withdraw")
    @Operation(summary = "출금 거래")
    @ApiResponse(responseCode = "200", description = "출금 성공")
    @ApiResponse(responseCode = "400", description = "잔액 부족") // 추가
    @ApiResponse(responseCode = "404", description = "지갑을 찾을 수 없음") // 추가
    public ResponseEntity<Map> withdraw(@PathVariable Long walletId, @RequestBody TransactionRequest request){
        Long withdrawAmount = request.amount();
        List<Transaction> transactionList;
        try {
          transactionList = transactionService.withdraw(walletId, withdrawAmount);
        }catch (IllegalArgumentException iae){
            return ResponseEntity.notFound().build();
        }catch (IllegalStateException ise){
            return ResponseEntity.badRequest().build();
        }

        Long balance = walletService.getWalletBalance(walletId);

        return ResponseEntity.ok(Map.of("balance", balance, "data", transactionList));
    }

    @PostMapping("/transfers")
    @Operation(summary = "이체 거래")
    @ApiResponse(responseCode = "200", description = "이체 성공")
    @ApiResponse(responseCode = "400", description = "잔액 부족") // 추가
    @ApiResponse(responseCode = "404", description = "지갑을 찾을 수 없음") // 추가
    public ResponseEntity<Map> transfer(@RequestBody TransactionRequest request){
        Long transferAmount = request.amount();
        Long fromId = request.fromWalletId();
        Long toId = request.toWalletId();

        List<Transaction> transactionList;
        try {
            transactionList = transactionService.transfer(fromId, toId, transferAmount);
        }catch (IllegalArgumentException iae){
            return ResponseEntity.notFound().build();
        }catch (IllegalStateException ise){
            return ResponseEntity.badRequest().build();
        }

        Long balance = walletService.getWalletBalance(fromId);

        return ResponseEntity.ok(Map.of("balance", balance, "data", transactionList));
    }

    @GetMapping("/wallets/{walletId}/transactions")
    @Operation(summary = "거래내역조회")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "404", description = "지갑을 찾을 수 없음") // 추가
    public ResponseEntity<Map> getTransactions(@PathVariable Long walletId){

        List<Transaction> transactionList;
        try {
            transactionList = transactionService.getTransactions(walletId);
        }catch (IllegalArgumentException iae){
            return ResponseEntity.notFound().build();
        }
        Long balance = walletService.getWalletBalance(walletId);

        return ResponseEntity.ok(Map.of("balance", balance, "data", transactionList));
    }

}
