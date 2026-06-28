package com.khs.wallet.service;

import com.khs.wallet.domain.Transaction;
import com.khs.wallet.domain.Wallet;
import com.khs.wallet.repository.TransactionRepository;
import com.khs.wallet.repository.WalletRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OptimisticTransactionServiceTest {

    @Mock
    private OptimisticTransactionExecutor executor;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private WalletRepository walletRepository;

    @InjectMocks
    private OptimisticTransactionService service;

    @Test
    void 입금_첫번째_시도에_성공() throws InterruptedException {
        List<Transaction> expected = List.of();
        when(executor.deposit(1L, 1000L)).thenReturn(expected);

        List<Transaction> result = service.deposit(1L, 1000L);

        assertThat(result).isEqualTo(expected);
        verify(executor, times(1)).deposit(1L, 1000L);
    }

    @Test
    void 입금_충돌_후_재시도_성공() throws InterruptedException {
        List<Transaction> expected = List.of();
        when(executor.deposit(1L, 1000L))
                .thenThrow(new ObjectOptimisticLockingFailureException(Wallet.class, 1L))
                .thenReturn(expected);

        List<Transaction> result = service.deposit(1L, 1000L);

        assertThat(result).isEqualTo(expected);
        verify(executor, times(2)).deposit(1L, 1000L);
    }

    @Test
    void 입금_최대재시도_초과시_예외발생() {
        when(executor.deposit(1L, 1000L))
                .thenThrow(new ObjectOptimisticLockingFailureException(Wallet.class, 1L));

        assertThatThrownBy(() -> service.deposit(1L, 1000L))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        verify(executor, times(3)).deposit(1L, 1000L);
    }

    @Test
    void 출금_충돌_후_재시도_성공() throws InterruptedException {
        List<Transaction> expected = List.of();
        when(executor.withdraw(1L, 500L))
                .thenThrow(new ObjectOptimisticLockingFailureException(Wallet.class, 1L))
                .thenThrow(new ObjectOptimisticLockingFailureException(Wallet.class, 1L))
                .thenReturn(expected);

        List<Transaction> result = service.withdraw(1L, 500L);

        assertThat(result).isEqualTo(expected);
        verify(executor, times(3)).withdraw(1L, 500L);
    }

    @Test
    void 이체_최대재시도_초과시_예외발생() {
        when(executor.transfer(1L, 2L, 500L))
                .thenThrow(new ObjectOptimisticLockingFailureException(Wallet.class, 1L));

        assertThatThrownBy(() -> service.transfer(1L, 2L, 500L))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        verify(executor, times(3)).transfer(1L, 2L, 500L);
    }

    @Test
    void 거래내역_조회_지갑없으면_예외() {
        when(walletRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getTransactions(99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("지갑을 찾을 수 없습니다");
    }
}
