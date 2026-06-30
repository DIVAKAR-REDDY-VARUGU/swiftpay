package com.swiftpay.gateway.payment;

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
        kafka.send(initiatedTopic, event.transactionId().toString(), event);
        log.info("Emitted PaymentInitiated topic={} txnId={}", initiatedTopic, event.transactionId());
    }
}
