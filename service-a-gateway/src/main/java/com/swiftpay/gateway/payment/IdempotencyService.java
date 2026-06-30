package com.swiftpay.gateway.payment;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

// Redis-based idempotency: a transaction_id may be processed only ONCE per 24h window.
@Service
public class IdempotencyService {

    private static final Duration WINDOW = Duration.ofHours(24);
    private final StringRedisTemplate redis;

    public IdempotencyService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    // SET key NX EX 86400 — returns true only the FIRST time we see this id (atomic claim); false = duplicate.
    public boolean claim(String transactionId) {
        Boolean firstTime = redis.opsForValue().setIfAbsent("idem:payment:" + transactionId, "1", WINDOW);
        return Boolean.TRUE.equals(firstTime);
    }
}
