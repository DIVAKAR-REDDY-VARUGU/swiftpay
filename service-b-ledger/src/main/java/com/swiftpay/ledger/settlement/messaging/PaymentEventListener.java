package com.swiftpay.ledger.settlement.messaging;

import com.swiftpay.ledger.settlement.event.PaymentInitiatedEvent;
import com.swiftpay.ledger.settlement.service.LedgerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

// The Kafka consumer: receives PaymentInitiated events and hands them to the ledger to settle.
@Component
public class PaymentEventListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventListener.class);

    private final LedgerService ledger;

    public PaymentEventListener(LedgerService ledger) {
        this.ledger = ledger;
    }

    @KafkaListener(topics = "${app.kafka.topics.payment-initiated}", groupId = "ledger-service")
    public void onPaymentInitiated(PaymentInitiatedEvent event) {
        log.info("Consumed PaymentInitiated txnId={}", event.transactionId());
        ledger.settle(event); // if this throws, Kafka redelivers -> safe because settle() is idempotent
    }
}
