package com.swiftpay.gateway.payment.messaging;

import com.swiftpay.gateway.payment.event.PaymentInitiatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

// Publishes payment events to Kafka. Key = transactionId, so all events for one payment keep order (same partition).
@Component
public class PaymentEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventPublisher.class);

    private final KafkaTemplate<String, Object> kafka;
    private final String initiatedTopic;

    public PaymentEventPublisher(KafkaTemplate<String, Object> kafka,
                                 @Value("${app.kafka.topics.payment-initiated}") String initiatedTopic) {
        this.kafka = kafka;
        this.initiatedTopic = initiatedTopic;
    }

    public void publishInitiated(PaymentInitiatedEvent event) {
        // This runs AFTER the DB commit, so the PENDING row already exists. If the send fails here, settlement
        // won't be triggered until reconciled — so make a lost publish loud rather than swallowing it.
        kafka.send(initiatedTopic, event.transactionId(), event).whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("FAILED to publish PaymentInitiated txnId={} - settlement will not trigger until reconciled",
                        event.transactionId(), ex);
            } else {
                log.info("Emitted PaymentInitiated topic={} txnId={}", initiatedTopic, event.transactionId());
            }
        });
    }
}
