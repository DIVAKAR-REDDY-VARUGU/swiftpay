package com.swiftpay.ledger.settlement.service;

import com.swiftpay.ledger.entity.Transaction;
import com.swiftpay.ledger.entity.TransactionStatus;
import com.swiftpay.ledger.repository.AccountRepository;
import com.swiftpay.ledger.repository.TransactionRepository;
import com.swiftpay.ledger.settlement.event.PaymentCompletedEvent;
import com.swiftpay.ledger.settlement.event.PaymentFailedEvent;
import com.swiftpay.ledger.settlement.event.PaymentInitiatedEvent;
import com.swiftpay.ledger.settlement.messaging.LedgerEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

// Settles a payment: atomic debit/credit, status update, outcome event — all in ONE DB transaction.
@Service
public class LedgerService {

    private static final Logger log = LoggerFactory.getLogger(LedgerService.class);

    private final AccountRepository accounts;
    private final TransactionRepository txRepo;
    private final LedgerEventPublisher publisher;
    private final StringRedisTemplate redis;

    public LedgerService(AccountRepository accounts, TransactionRepository txRepo,
                         LedgerEventPublisher publisher, StringRedisTemplate redis) {
        this.accounts = accounts;
        this.txRepo = txRepo;
        this.publisher = publisher;
        this.redis = redis;
    }

    @Transactional
    public void settle(PaymentInitiatedEvent e) {
        // Lock the tx row (SELECT ... FOR UPDATE). The gateway publishes only AFTER its PENDING row is committed,
        // so the row should be here; if a redelivery still beats the commit (rare), throw -> Kafka redelivers.
        Transaction tx = txRepo.findByIdForUpdate(e.transactionId()).orElseThrow(() ->
                new IllegalStateException("transaction row not visible yet for " + e.transactionId() + " - will retry"));

        // IDEMPOTENCY: only a PENDING tx is settled. A redelivery of an already-settled one is a no-op.
        // The row lock above means a concurrent duplicate waits here and then sees a non-PENDING status.
        if (tx.getStatus() != TransactionStatus.PENDING) {
            log.info("Idempotent skip: txn {} already {}", tx.getId(), tx.getStatus());
            return;
        }

        // The receiver must exist before we move any money. (The gateway checks too, but only against a Redis
        // cache, which isn't authoritative — so we must not debit the sender unless we can credit the receiver.)
        if (!accounts.existsById(e.receiverId())) {
            tx.markFailed("receiver account not found");
            txRepo.save(tx);
            publisher.publishFailed(new PaymentFailedEvent(tx.getId(), "receiver account not found"));
            log.warn("FAILED txn {}: receiver {} not found", tx.getId(), e.receiverId());
            return;
        }

        // ATOMIC, RACE-SAFE DEBIT: returns 0 if the sender can't cover it -> insufficient funds.
        int debited = accounts.debit(e.senderId(), e.amount());
        if (debited == 0) {
            tx.markFailed("insufficient funds");
            txRepo.save(tx);
            publisher.publishFailed(new PaymentFailedEvent(tx.getId(), "insufficient funds"));
            log.warn("FAILED txn {}: insufficient funds (sender {})", tx.getId(), e.senderId());
            return;
        }

        // Credit the receiver. If 0 rows changed (receiver vanished after the check above), abort the WHOLE
        // transaction so the debit rolls back too — never debit without a matching credit (double-entry).
        int credited = accounts.credit(e.receiverId(), e.amount());
        if (credited == 0) {
            throw new IllegalStateException("credit failed for receiver " + e.receiverId() + " - rolling back debit");
        }

        tx.markCompleted();
        txRepo.save(tx);

        // Evict the gateway's cached balances AFTER commit, so a concurrent read during the commit window
        // can't repopulate Redis with the stale (pre-debit) value.
        String senderKey = "balance:" + e.senderId();
        String receiverKey = "balance:" + e.receiverId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                redis.delete(senderKey);
                redis.delete(receiverKey);
            }
        });

        publisher.publishCompleted(new PaymentCompletedEvent(tx.getId(), e.senderId(), e.receiverId(), e.amount()));
        log.info("COMPLETED txn {}: {} -> {} amount {}", tx.getId(), e.senderId(), e.receiverId(), e.amount());
    }
}
