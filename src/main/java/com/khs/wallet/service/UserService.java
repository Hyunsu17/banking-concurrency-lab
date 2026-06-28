package com.khs.wallet.service;

import com.khs.wallet.domain.User;
import com.khs.wallet.domain.Wallet;
import com.khs.wallet.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    private final WalletService walletService;

    @Transactional
    public Wallet signUpUser(String name){
        User user = User.builder().name(name).build();
        Long userId = userRepository.save(user).getId();

        Wallet wallet = walletService.createWallet(userId);

        return wallet;

    }
}
