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

// plain unit tests for the settle() logic. everything is mocked (db, redis, kafka) so im only
// checking the branches in the code, not the real database. the integration test does the real db part.
@ExtendWith(MockitoExtension.class)
class LedgerServiceTest {

    @Mock AccountRepository accounts;
    @Mock TransactionRepository txRepo;
    @Mock LedgerEventPublisher publisher;
    @Mock StringRedisTemplate redis;
    @InjectMocks LedgerService ledger;

    private static final BigDecimal AMOUNT = new BigDecimal("100.00");

    // small helper so every test isnt repeating the same event
    private PaymentInitiatedEvent event(String id) {
        return new PaymentInitiatedEvent(id, 1L, 2L, AMOUNT, "INR");
    }

    @Test
    void settlesAPendingPayment() {
        // the normal case. txn is PENDING, receiver exists, sender can cover it.
        // so it should debit the sender, credit the receiver, mark it COMPLETED, fire the event
        // and clear the two cached balances so the gateway reads fresh values next time.
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
        // sender doesnt have enough. the debit query returns 0 rows (the where balance >= amount guard).
        // so we must mark it FAILED and NOT credit the receiver - otherwise money appears from nowhere.
        Transaction tx = mock(Transaction.class);
        when(tx.getId()).thenReturn("T2");
        when(tx.getStatus()).thenReturn(TransactionStatus.PENDING);
        when(txRepo.findByIdForUpdate("T2")).thenReturn(Optional.of(tx));
        when(accounts.existsById(2L)).thenReturn(true);
        when(accounts.debit(1L, AMOUNT)).thenReturn(0);

        ledger.settle(event("T2"));

        verify(tx).markFailed("insufficient funds");
        verify(accounts, never()).credit(anyLong(), any());
        verify(publisher).publishFailed(any(PaymentFailedEvent.class));
    }

    @Test
    void marksFailedWhenReceiverMissing() {
        // receiver account isnt there. we check this BEFORE moving any money, so the sender
        // never gets debited for a payment that can never land. just fail it.
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
        // kafka is at-least-once so the same event can show up twice. if the txn is already
        // COMPLETED we just skip it - no second debit, nothing saved, nothing published. this is the idempotency.
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
        // edge case from the race we fixed: the event arrives before the gateway's PENDING row is committed.
        // instead of silently dropping it, we throw so kafka redelivers it later when the row is there.
        when(txRepo.findByIdForUpdate("T5")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ledger.settle(event("T5")))
                .isInstanceOf(IllegalStateException.class);
    }
}
