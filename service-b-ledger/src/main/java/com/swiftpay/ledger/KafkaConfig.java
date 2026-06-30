package com.swiftpay.ledger;

import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

// RESILIENCE: if processing a message fails (e.g. DB temporarily down), retry a few times,
// then route the message to a Dead-Letter Topic (<topic>.DLT) so it doesn't block the partition.
@Configuration
public class KafkaConfig {

    @Bean
    public DefaultErrorHandler errorHandler(KafkaTemplate<String, Object> template) {
        // after retries are exhausted, publish the failed record to "<original-topic>.DLT"
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                template, (record, ex) -> new TopicPartition(record.topic() + ".DLT", -1));
        // retry 3 times, 2 seconds apart, then send to the DLT
        return new DefaultErrorHandler(recoverer, new FixedBackOff(2000L, 3L));
    }
}
