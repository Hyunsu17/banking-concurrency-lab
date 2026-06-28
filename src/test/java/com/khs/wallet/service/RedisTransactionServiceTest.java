package com.khs.wallet.service;

import com.khs.wallet.domain.Transaction;
import com.khs.wallet.exception.LockAcquisitionException;
import com.khs.wallet.repository.TransactionRepository;
import com.khs.wallet.repository.WalletRepository;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
public class RedisTransactionServiceTest {

    @Mock
    RedissonClient redissonClient;
    @Mock
    OptimisticTransactionExecutor executor;
    @Mock
    WalletRepository walletRepository;
    @Mock
    TransactionRepository transactionRepository;
    @Mock
    RLock lock;

    @InjectMocks
    RedisTransactionService service;
    private ThrowableAssert.ThrowingCallable LockAcquisitionException;

    private void 락_획득_성공() throws InterruptedException {
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
    }

    private void 락_획득_실패() throws InterruptedException {
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(false);
    }

    @Test
    void 입금_락_획득_후_executor_호출() throws InterruptedException {
        //given
        락_획득_성공();
        List<Transaction> expected = List.of();
        when(executor.deposit(1L, 1000L)).thenReturn(expected);

        //when
        List<Transaction> result = service.deposit(1L, 1000L);

        //then
        assertThat(result).isEqualTo(expected);
        verify(executor, times(1)).deposit(1L, 1000L);
        verify(lock, times(1)).unlock();
    }

    @Test
    void 입금_락_획득_실패시_executor_호출안함() throws InterruptedException {
        //given
        락_획득_실패();

        //then
        assertThatThrownBy(() -> service.deposit(1L, 1000L)).isInstanceOf(LockAcquisitionException.class);
        verify(executor, never()).deposit(anyLong(), anyLong());
        verify(lock, never()).unlock();
    }

    @Test
    void 입금_executor_예외시_락_반드시_해제() throws InterruptedException {
        // given
        락_획득_성공();
        //여기서 given으로 무조건 runtime던지도록 해야할듯 이게맞나
        when(executor.deposit(1L, 1000L)).thenThrow(RuntimeException.class);

        // when & then
        assertThatThrownBy(() -> service.deposit(1L, 1000L)).isInstanceOf(RuntimeException.class);

        // then
        verify(lock, times(1)).unlock();
    }

    // ── withdraw ────────────────────────────────────────────
    @Test
    void 출금_락_획득_후_executor_호출() throws InterruptedException {
        //given
        락_획득_성공();
        List<Transaction> expected = List.of();
        when(executor.withdraw(1L, 1000L)).thenReturn(expected);

        //when
        List<Transaction> result = service.withdraw(1L, 1000L);

        //then
        assertThat(result).isEqualTo(expected);
        verify(executor, times(1)).withdraw(1L, 1000L);
        verify(lock, times(1)).unlock();
    }

    @Test
    void 출금_잔액부족시_락_반드시_해제() throws InterruptedException {
        // given
        락_획득_성공();
        when(executor.withdraw(1L, 1000L)).thenThrow(new IllegalStateException("잔액이 부족합니다"));

        // when & then
        assertThatThrownBy(() -> service.withdraw(1L, 1000L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("잔액이 부족합니다");

        // then
        verify(lock, times(1)).unlock();

    }

    // ── transfer ────────────────────────────────────────────

    @Test
    void 이체_낮은ID_먼저_락_획득() throws InterruptedException {
        // given
        RLock lock1 = Mockito.mock(RLock.class);
        RLock lock2 = Mockito.mock(RLock.class);
        when(redissonClient.getLock("wallet:lock:1")).thenReturn(lock1);
        when(redissonClient.getLock("wallet:lock:2")).thenReturn(lock2);
        when(lock1.tryLock(anyLong(), anyLong(), any())).thenReturn(true);
        when(lock2.tryLock(anyLong(), anyLong(), any())).thenReturn(true);

        // when
        service.transfer(2L, 1L, 500L);

        // then 순서 검증: lock1 먼저, lock2 나중
        var order = inOrder(lock1, lock2);
        order.verify(lock1).tryLock(anyLong(), anyLong(), any());
        order.verify(lock2).tryLock(anyLong(), anyLong(), any());
    }

    @Test
    void 이체_두_락_모두_해제() throws InterruptedException {
        // given
        RLock lock1 = Mockito.mock(RLock.class);
        RLock lock2 = Mockito.mock(RLock.class);
        when(redissonClient.getLock("wallet:lock:1")).thenReturn(lock1);
        when(redissonClient.getLock("wallet:lock:2")).thenReturn(lock2);
        when(lock1.tryLock(anyLong(), anyLong(), any())).thenReturn(true);
        when(lock2.tryLock(anyLong(), anyLong(), any())).thenReturn(true);

        // when
        service.transfer(2L, 1L, 500L);

        // then 두락 모두 해제

        verify(lock1, times(1)).unlock();
        verify(lock2, times(1)).unlock();
    }

}
