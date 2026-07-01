package com.swiftpay.analytics.consumer;

import com.swiftpay.analytics.event.PaymentOutcomeEvent;
import com.swiftpay.analytics.service.AnalyticsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

// Subscribes to payments.completed on its OWN consumer group, so it gets an independent copy of every
// outcome the ledger emits (pub/sub fan-out). Each event becomes one row in ClickHouse.
@Component
public class PaymentOutcomeListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentOutcomeListener.class);

    private final AnalyticsService analytics;

    public PaymentOutcomeListener(AnalyticsService analytics) {
        this.analytics = analytics;
    }

    @KafkaListener(topics = "${app.kafka.topics.payment-completed}", groupId = "analytics-service")
    public void onOutcome(PaymentOutcomeEvent event) {
        log.info("Recording outcome txnId={} status={}", event.transactionId(), event.status());
        analytics.record(event);
    }
}
