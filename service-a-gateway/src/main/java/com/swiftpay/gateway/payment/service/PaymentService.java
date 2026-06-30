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
import com.swiftpay.gateway.shared.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

// Gateway payment logic: idempotency (Redis + DB) -> validate (Redis-cached balance) -> save PENDING -> emit event.
// Also serves payment reads (status, history). BalanceCache lives in the account module (cross-module dependency).
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final TransactionRepository txRepo;
    private final BalanceCache balanceCache;
    private final IdempotencyService idempotency;
    private final PaymentEventPublisher publisher;

    public PaymentService(TransactionRepository txRepo, BalanceCache balanceCache,
                          IdempotencyService idempotency, PaymentEventPublisher publisher) {
        this.txRepo = txRepo;
        this.balanceCache = balanceCache;
        this.idempotency = idempotency;
        this.publisher = publisher;
    }

    @Transactional
    public PaymentResponse initiate(PaymentRequest req) {
        // transactionId IS the idempotency key. Client-supplied (e.g. "Trans-3-1_1_100"), or we generate one.
        String txnId = req.transactionId() != null ? req.transactionId() : UUID.randomUUID().toString();

        // 1a. Durable idempotency backstop — already-processed request returns its existing result.
        var existing = txRepo.findById(txnId);
        if (existing.isPresent()) {
            Transaction t = existing.get();
            log.info("Idempotent hit (db) txnId={} status={}", txnId, t.getStatus());
            return new PaymentResponse(t.getId(), t.getStatus(), "Duplicate request - returning existing transaction");
        }

        // 1b. Fast Redis idempotency — atomic SET NX EX (24h window). Loser is a duplicate.
        if (!idempotency.claim(txnId)) {
            log.info("Idempotent hit (redis) txnId={}", txnId);
            return new PaymentResponse(txnId, TransactionStatus.PENDING, "Duplicate request - already received");
        }

        // 2. Validate (Redis-cached balance lookup; authoritative debit still happens atomically in the ledger).
        if (req.senderId().equals(req.receiverId())) {
            throw new BadRequestException("sender and receiver must differ");
        }
        BigDecimal senderBalance = balanceCache.balanceOf(req.senderId());           // throws 404 if sender missing
        if (!balanceCache.exists(req.receiverId())) {
            throw new NotFoundException("receiver account " + req.receiverId() + " not found");
        }
        if (senderBalance.compareTo(req.amount()) < 0) {
            throw new InsufficientFundsException("sender " + req.senderId() + " has insufficient funds");
        }

        // 3. Save PENDING
        Transaction tx = new Transaction(txnId, req.senderId(), req.receiverId(),
                req.amount(), req.currency(), TransactionStatus.PENDING);
        txRepo.save(tx);
        log.info("Saved PENDING txnId={} {}->{} {} {}", txnId, req.senderId(), req.receiverId(), req.amount(), req.currency());

        // 4. Emit event — but ONLY AFTER this transaction commits, so the ledger always finds the PENDING row.
        //    (Publishing inside the tx races the consumer: it can read before our commit -> "no transaction row" -> stuck PENDING.)
        PaymentInitiatedEvent event = new PaymentInitiatedEvent(
                txnId, req.senderId(), req.receiverId(), req.amount(), req.currency());
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publisher.publishInitiated(event);
                }
            });
        } else {
            publisher.publishInitiated(event); // no active transaction (e.g. a unit test) — publish immediately
        }

        // 5. Respond (settlement is async)
        return new PaymentResponse(txnId, TransactionStatus.PENDING, "Payment accepted and is being processed");
    }

    // read: current status of one transaction
    public Transaction getStatus(String id) {
        return txRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("transaction " + id + " not found"));
    }

    // read: a user's transaction history (as sender or receiver), newest first
    public List<Transaction> history(Long userId) {
        return txRepo.findBySenderIdOrReceiverIdOrderByCreatedAtDesc(userId, userId);
    }
}
