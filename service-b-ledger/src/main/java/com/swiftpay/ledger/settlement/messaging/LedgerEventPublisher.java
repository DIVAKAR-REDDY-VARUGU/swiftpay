package com.swiftpay.ledger.settlement.messaging;

import com.swiftpay.ledger.settlement.event.PaymentCompletedEvent;
import com.swiftpay.ledger.settlement.event.PaymentFailedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

// publishes the completed/failed settlement outcome to kafka.
// heads up: nothing consumes payments.completed yet - that's intentional, not a miss.
// the ledger's job is done once money moved + the event is out. downstream stuff
// (notifications, analytics/clickhouse, webhooks) can subscribe to this topic later
// without me touching the ledger. pub/sub - the producer doesn't care who's listening.
@Component
public class LedgerEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(LedgerEventPublisher.class);

    private final KafkaTemplate<String, Object> kafka;
    private final String completedTopic;

    public LedgerEventPublisher(KafkaTemplate<String, Object> kafka,
                                @Value("${app.kafka.topics.payment-completed}") String completedTopic) {
        this.kafka = kafka;
        this.completedTopic = completedTopic;
    }

    public void publishCompleted(PaymentCompletedEvent e) {
        kafka.send(completedTopic, e.transactionId(), e);
        log.info("Emitted PaymentCompleted txnId={}", e.transactionId());
    }

    public void publishFailed(PaymentFailedEvent e) {
        kafka.send(completedTopic, e.transactionId(), e);
        log.info("Emitted PaymentFailed txnId={} reason={}", e.transactionId(), e.reason());
    }
}
