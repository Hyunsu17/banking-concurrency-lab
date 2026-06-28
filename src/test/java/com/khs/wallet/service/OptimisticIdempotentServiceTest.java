package com.khs.wallet.service;

import com.khs.wallet.domain.Transaction;
import com.khs.wallet.domain.TransactionType;
import com.khs.wallet.repository.TransactionRepository;
import com.khs.wallet.repository.WalletRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class OptimisticIdempotentServiceTest {

    @Mock
    TransactionRepository transactionRepository;

    @Mock
    WalletRepository walletRepository;

    @Mock
    OptimisticIdempotentExecutor executor;

    @Spy
    OptimisticRetryTemplate retryTemplate;

    @InjectMocks
    OptimisticIdempotentService service;

    @Test
    void 이미_처리된_키면_executor_호출_안함() throws InterruptedException {
        String key = "test-key-123";
        given(transactionRepository.findByIdempotencyKey(key))
                .willReturn(Optional.of(Transaction.builder().idempotencyKey(key).build()));

        service.withdraw(1L, 100L, key);

        verify(executor, never()).withdraw(any(), any(), any());
    }

    @Test
    void 새로운_키면_executor_호출함() throws InterruptedException {
        String key = "test-key-123";
        given(transactionRepository.findByIdempotencyKey(key))
                .willReturn(Optional.empty());

        service.withdraw(1L, 100L, key);

        verify(executor).withdraw(1L, 100L, key);
    }

    @Test
    void transfer_중복_체크_시_TRANSFER_OUT_suffix_키로_조회함() throws InterruptedException {
        String key = "test-key-123";
        String expectedLookupKey = key + ":" + TransactionType.TRANSFER_OUT.name();
        given(transactionRepository.findByIdempotencyKey(expectedLookupKey))
                .willReturn(Optional.of(Transaction.builder().idempotencyKey(expectedLookupKey).build()));

        service.transfer(1L, 2L, 100L, key);

        verify(transactionRepository).findByIdempotencyKey(expectedLookupKey);
        verify(executor, never()).transfer(any(), any(), any(), any());
    }

    @Test
    void DB_중복키_예외_발생_시_예외_전파_없이_거래내역_반환() throws InterruptedException {
        String key = "test-key-123";
        given(transactionRepository.findByIdempotencyKey(key))
                .willReturn(Optional.empty());
        given(executor.withdraw(1L, 100L, key))
                .willThrow(new DataIntegrityViolationException("duplicate key"));

        service.withdraw(1L, 100L, key);

        verify(transactionRepository).findByWalletIdOrderByCreatedAtAsc(1L);
    }
}
