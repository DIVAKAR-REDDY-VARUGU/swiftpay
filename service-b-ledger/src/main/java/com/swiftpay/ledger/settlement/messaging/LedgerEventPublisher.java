package com.swiftpay.ledger.settlement.messaging;

import com.swiftpay.ledger.settlement.event.PaymentOutcomeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

// publishes the settlement outcome (one event, COMPLETED or FAILED) to kafka.
// heads up: nothing in THIS repo consumes payments.completed - thats on purpose. the analytics
// service (service C) subscribes to it. pub/sub - the producer doesnt care who's listening.
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

    public void publishOutcome(PaymentOutcomeEvent e) {
        kafka.send(completedTopic, e.transactionId(), e);
        log.info("Emitted PaymentOutcome txnId={} status={}", e.transactionId(), e.status());
    }
}
