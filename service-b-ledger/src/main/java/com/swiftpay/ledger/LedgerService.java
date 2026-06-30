package com.swiftpay.ledger;

import com.swiftpay.ledger.account.AccountRepository;
import com.swiftpay.ledger.event.PaymentCompletedEvent;
import com.swiftpay.ledger.event.PaymentFailedEvent;
import com.swiftpay.ledger.event.PaymentInitiatedEvent;
import com.swiftpay.ledger.payment.Transaction;
import com.swiftpay.ledger.payment.TransactionRepository;
import com.swiftpay.ledger.payment.TransactionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        Transaction tx = txRepo.findById(e.transactionId()).orElse(null);
        if (tx == null) {
            log.warn("No transaction row for {} - ignoring", e.transactionId());
            return;
        }

        // IDEMPOTENCY: Kafka is at-least-once, so the same event may arrive twice. Only settle a PENDING one.
        if (tx.getStatus() != TransactionStatus.PENDING) {
            log.info("Idempotent skip: txn {} already {}", tx.getId(), tx.getStatus());
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

        accounts.credit(e.receiverId(), e.amount());
        tx.markCompleted();
        txRepo.save(tx);

        // invalidate the gateway's cached balances so the next read is fresh (cache-aside eviction)
        redis.delete("balance:" + e.senderId());
        redis.delete("balance:" + e.receiverId());

        publisher.publishCompleted(new PaymentCompletedEvent(tx.getId(), e.senderId(), e.receiverId(), e.amount()));
        log.info("COMPLETED txn {}: {} -> {} amount {}", tx.getId(), e.senderId(), e.receiverId(), e.amount());
    }
}
