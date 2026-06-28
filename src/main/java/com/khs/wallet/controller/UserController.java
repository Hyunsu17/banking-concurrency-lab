package com.khs.wallet.controller;

import com.khs.wallet.domain.Wallet;
import com.khs.wallet.service.UserService;
import com.khs.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    private final WalletService walletService;

    @PostMapping("/signUp")
    public ResponseEntity<WalletResponse> signUpUserWallet(@RequestBody SignUpRequest request){
         Wallet wallet= userService.signUpUser(request.name());

        return ResponseEntity.ok(new WalletResponse(wallet.getId(), wallet.getBalance()));
    }
}
