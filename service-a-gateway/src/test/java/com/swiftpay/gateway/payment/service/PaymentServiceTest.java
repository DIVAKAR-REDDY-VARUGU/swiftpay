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

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock TransactionRepository txRepo;
    @Mock BalanceCache balanceCache;
    @Mock IdempotencyService idempotency;
    @Mock PaymentEventPublisher publisher;
    @InjectMocks PaymentService service;

    private PaymentRequest req(String id, long from, long to, String amount) {
        return new PaymentRequest(id, from, to, new BigDecimal(amount), "INR");
    }

    @Test
    void acceptsAndPublishesAValidPayment() {
        when(txRepo.findById("P1")).thenReturn(Optional.empty());
        when(idempotency.claim("P1")).thenReturn(true);
        when(balanceCache.balanceOf(1L)).thenReturn(new BigDecimal("1000"));
        when(balanceCache.exists(2L)).thenReturn(true);

        PaymentResponse res = service.initiate(req("P1", 1, 2, "100"));

        assertThat(res.status()).isEqualTo(TransactionStatus.PENDING);
        verify(txRepo).save(any(Transaction.class));
        verify(publisher).publishInitiated(any(PaymentInitiatedEvent.class)); // no active tx in a unit test -> immediate
    }

    @Test
    void returnsExistingTransactionOnDuplicate() {
        Transaction existing = new Transaction("P2", 1L, 2L, new BigDecimal("100"), "INR", TransactionStatus.COMPLETED);
        when(txRepo.findById("P2")).thenReturn(Optional.of(existing));

        PaymentResponse res = service.initiate(req("P2", 1, 2, "100"));

        assertThat(res.status()).isEqualTo(TransactionStatus.COMPLETED);
        verify(txRepo, never()).save(any());
        verifyNoInteractions(publisher);
    }

    @Test
    void treatsLostRedisClaimAsDuplicate() {
        when(txRepo.findById("P3")).thenReturn(Optional.empty());
        when(idempotency.claim("P3")).thenReturn(false);   // someone already claimed this id

        PaymentResponse res = service.initiate(req("P3", 1, 2, "100"));

        assertThat(res.status()).isEqualTo(TransactionStatus.PENDING);
        verify(txRepo, never()).save(any());
        verifyNoInteractions(publisher);
    }

    @Test
    void rejectsSenderEqualsReceiver() {
        when(txRepo.findById("P4")).thenReturn(Optional.empty());
        when(idempotency.claim("P4")).thenReturn(true);

        assertThatThrownBy(() -> service.initiate(req("P4", 1, 1, "100")))
                .isInstanceOf(BadRequestException.class);
        verify(txRepo, never()).save(any());
    }

    @Test
    void rejectsInsufficientFunds() {
        when(txRepo.findById("P5")).thenReturn(Optional.empty());
        when(idempotency.claim("P5")).thenReturn(true);
        when(balanceCache.balanceOf(1L)).thenReturn(new BigDecimal("50"));
        when(balanceCache.exists(2L)).thenReturn(true);

        assertThatThrownBy(() -> service.initiate(req("P5", 1, 2, "100")))
                .isInstanceOf(InsufficientFundsException.class);
        verify(publisher, never()).publishInitiated(any());
    }
}
