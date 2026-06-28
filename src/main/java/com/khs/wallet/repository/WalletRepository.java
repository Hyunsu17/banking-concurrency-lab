package com.khs.wallet.repository;

import com.khs.wallet.domain.Wallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface WalletRepository extends JpaRepository<Wallet, Long> {

    //비관적 락사용
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.ID = :id")
    Optional<Wallet> findByIdForUpdate(@Param("id") Long id);


}
