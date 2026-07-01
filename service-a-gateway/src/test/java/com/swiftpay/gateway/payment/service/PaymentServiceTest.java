package com.swiftpay.gateway.payment.service;

import com.swiftpay.gateway.account.service.BalanceCache;
import com.swiftpay.gateway.entity.Transaction;
import com.swiftpay.gateway.entity.TransactionStatus;
import com.swiftpay.gateway.payment.dto.PaymentRequest;
import com.swiftpay.gateway.payment.dto.PaymentResponse;
import com.swiftpay.gateway.payment.event.PaymentInitiatedEvent;
import com.swiftpay.gateway.payment.messaging.PaymentEventPublisher;
import com.swiftpay.gateway.repository.TransactionRepository;
import com.swiftpay.gateway.shared.exception.BadRequestException;
import com.swiftpay.gateway.shared.exception.InsufficientFundsException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// unit tests for the gateway's initiate() logic. repo, redis idempotency, balance cache and kafka
// are all mocked, so im just checking the decisions it makes (accept, duplicate, reject).
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock TransactionRepository txRepo;
    @Mock BalanceCache balanceCache;
    @Mock IdempotencyService idempotency;
    @Mock PaymentEventPublisher publisher;
    @InjectMocks PaymentService service;

    // helper to build a request quickly
    private PaymentRequest req(String id, long from, long to, String amount) {
        return new PaymentRequest(id, from, to, new BigDecimal(amount), "INR");
    }

    @Test
    void acceptsAndPublishesAValidPayment() {
        // happy path. brand new txn id, sender has money, receiver exists.
        // so it should save the row as PENDING and publish the event for the ledger to pick up.
        when(txRepo.findById("P1")).thenReturn(Optional.empty());
        when(idempotency.claim("P1")).thenReturn(true);
        when(balanceCache.balanceOf(1L)).thenReturn(new BigDecimal("1000"));
        when(balanceCache.exists(2L)).thenReturn(true);

        PaymentResponse res = service.initiate(req("P1", 1, 2, "100"));

        assertThat(res.status()).isEqualTo(TransactionStatus.PENDING);
        verify(txRepo).save(any(Transaction.class));
        verify(publisher).publishInitiated(any(PaymentInitiatedEvent.class));
    }

    @Test
    void returnsExistingTransactionOnDuplicate() {
        // same txn id is already in the db (a retry). we must NOT charge again - just hand back
        // whatever we already have. no save, no new event. this is the durable idempotency check.
        Transaction existing = new Transaction("P2", 1L, 2L, new BigDecimal("100"), "INR", TransactionStatus.COMPLETED);
        when(txRepo.findById("P2")).thenReturn(Optional.of(existing));

        PaymentResponse res = service.initiate(req("P2", 1, 2, "100"));

        assertThat(res.status()).isEqualTo(TransactionStatus.COMPLETED);
        verify(txRepo, never()).save(any());
        verifyNoInteractions(publisher);
    }

    @Test
    void treatsLostRedisClaimAsDuplicate() {
        // not in the db yet, but redis says someone already claimed this id (claim returns false).
        // that means a near-simultaneous duplicate - treat it as one too, dont save or publish.
        when(txRepo.findById("P3")).thenReturn(Optional.empty());
        when(idempotency.claim("P3")).thenReturn(false);

        PaymentResponse res = service.initiate(req("P3", 1, 2, "100"));

        assertThat(res.status()).isEqualTo(TransactionStatus.PENDING);
        verify(txRepo, never()).save(any());
        verifyNoInteractions(publisher);
    }

    @Test
    void rejectsSenderEqualsReceiver() {
        // you cant pay yourself. should throw a bad request and never save anything.
        when(txRepo.findById("P4")).thenReturn(Optional.empty());
        when(idempotency.claim("P4")).thenReturn(true);

        assertThatThrownBy(() -> service.initiate(req("P4", 1, 1, "100")))
                .isInstanceOf(BadRequestException.class);
        verify(txRepo, never()).save(any());
    }

    @Test
    void rejectsInsufficientFunds() {
        // sender only has 50 but is trying to send 100. reject it here and dont publish anything.
        // (the ledger checks the real balance again later, this is just an early bail out.)
        when(txRepo.findById("P5")).thenReturn(Optional.empty());
        when(idempotency.claim("P5")).thenReturn(true);
        when(balanceCache.balanceOf(1L)).thenReturn(new BigDecimal("50"));
        when(balanceCache.exists(2L)).thenReturn(true);

        assertThatThrownBy(() -> service.initiate(req("P5", 1, 2, "100")))
                .isInstanceOf(InsufficientFundsException.class);
        verify(publisher, never()).publishInitiated(any());
    }
}
