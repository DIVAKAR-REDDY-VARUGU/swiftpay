package com.swiftpay.ledger;

import com.swiftpay.ledger.event.PaymentCompletedEvent;
import com.swiftpay.ledger.event.PaymentFailedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

// Publishes the outcome (completed/failed) back to Kafka so other services (e.g. notifications, analytics) can react.
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
        kafka.send(completedTopic, e.transactionId().toString(), e);
        log.info("Emitted PaymentCompleted txnId={}", e.transactionId());
    }

    public void publishFailed(PaymentFailedEvent e) {
        kafka.send(completedTopic, e.transactionId().toString(), e);
        log.info("Emitted PaymentFailed txnId={} reason={}", e.transactionId(), e.reason());
    }
}
