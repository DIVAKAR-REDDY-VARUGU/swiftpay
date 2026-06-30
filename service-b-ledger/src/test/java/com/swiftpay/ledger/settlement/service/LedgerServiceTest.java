package com.swiftpay.ledger.settlement.service;

import com.swiftpay.ledger.entity.Transaction;
import com.swiftpay.ledger.entity.TransactionStatus;
import com.swiftpay.ledger.repository.AccountRepository;
import com.swiftpay.ledger.repository.TransactionRepository;
import com.swiftpay.ledger.settlement.event.PaymentCompletedEvent;
import com.swiftpay.ledger.settlement.event.PaymentFailedEvent;
import com.swiftpay.ledger.settlement.event.PaymentInitiatedEvent;
import com.swiftpay.ledger.settlement.messaging.LedgerEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LedgerServiceTest {

    @Mock AccountRepository accounts;
    @Mock TransactionRepository txRepo;
    @Mock LedgerEventPublisher publisher;
    @Mock StringRedisTemplate redis;
    @InjectMocks LedgerService ledger;

    private static final BigDecimal AMOUNT = new BigDecimal("100.00");

    private PaymentInitiatedEvent event(String id) {
        return new PaymentInitiatedEvent(id, 1L, 2L, AMOUNT, "INR");
    }

    @Test
    void settlesAPendingPayment() {
        Transaction tx = mock(Transaction.class);
        when(tx.getId()).thenReturn("T1");
        when(tx.getStatus()).thenReturn(TransactionStatus.PENDING);
        when(txRepo.findByIdForUpdate("T1")).thenReturn(Optional.of(tx));
        when(accounts.existsById(2L)).thenReturn(true);
        when(accounts.debit(1L, AMOUNT)).thenReturn(1);
        when(accounts.credit(2L, AMOUNT)).thenReturn(1);

        ledger.settle(event("T1"));

        verify(accounts).debit(1L, AMOUNT);
        verify(accounts).credit(2L, AMOUNT);
        verify(tx).markCompleted();
        verify(txRepo).save(tx);
        verify(publisher).publishCompleted(any(PaymentCompletedEvent.class));
        verify(redis).delete("balance:1");
        verify(redis).delete("balance:2");
    }

    @Test
    void marksFailedOnInsufficientFunds() {
        Transaction tx = mock(Transaction.class);
        when(tx.getId()).thenReturn("T2");
        when(tx.getStatus()).thenReturn(TransactionStatus.PENDING);
        when(txRepo.findByIdForUpdate("T2")).thenReturn(Optional.of(tx));
        when(accounts.existsById(2L)).thenReturn(true);
        when(accounts.debit(1L, AMOUNT)).thenReturn(0);   // sender can't cover it

        ledger.settle(event("T2"));

        verify(tx).markFailed("insufficient funds");
        verify(accounts, never()).credit(anyLong(), any());
        verify(publisher).publishFailed(any(PaymentFailedEvent.class));
    }

    @Test
    void marksFailedWhenReceiverMissing() {
        Transaction tx = mock(Transaction.class);
        when(tx.getId()).thenReturn("T3");
        when(tx.getStatus()).thenReturn(TransactionStatus.PENDING);
        when(txRepo.findByIdForUpdate("T3")).thenReturn(Optional.of(tx));
        when(accounts.existsById(2L)).thenReturn(false);

        ledger.settle(event("T3"));

        verify(tx).markFailed("receiver account not found");
        verify(accounts, never()).debit(anyLong(), any());
        verify(publisher).publishFailed(any(PaymentFailedEvent.class));
    }

    @Test
    void skipsAnAlreadySettledPayment() {
        Transaction tx = mock(Transaction.class);
        when(tx.getStatus()).thenReturn(TransactionStatus.COMPLETED);
        when(txRepo.findByIdForUpdate("T4")).thenReturn(Optional.of(tx));

        ledger.settle(event("T4"));

        verify(accounts, never()).debit(anyLong(), any());
        verify(txRepo, never()).save(any());
        verifyNoInteractions(publisher);
    }

    @Test
    void retriesWhenRowNotYetVisible() {
        when(txRepo.findByIdForUpdate("T5")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ledger.settle(event("T5")))
                .isInstanceOf(IllegalStateException.class);
    }
}
