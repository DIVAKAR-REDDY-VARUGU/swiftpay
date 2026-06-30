package com.swiftpay.gateway.account;

import com.swiftpay.gateway.common.NotFoundException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;

// Redis cache-aside for balance lookups: read from Redis, fall back to Postgres on a miss, cache with a short TTL.
@Service
public class BalanceCache {

    private static final Duration TTL = Duration.ofSeconds(30);
    private final StringRedisTemplate redis;
    private final AccountRepository accounts;

    public BalanceCache(StringRedisTemplate redis, AccountRepository accounts) {
        this.redis = redis;
        this.accounts = accounts;
    }

    public BigDecimal balanceOf(Long accountId) {
        String key = "balance:" + accountId;
        String cached = redis.opsForValue().get(key);
        if (cached != null) {
            return new BigDecimal(cached);            // cache hit
        }
        Account a = accounts.findById(accountId)       // miss -> read DB
                .orElseThrow(() -> new NotFoundException("account " + accountId + " not found"));
        redis.opsForValue().set(key, a.getBalance().toPlainString(), TTL);  // populate cache
        return a.getBalance();
    }

    public boolean exists(Long accountId) {
        return accounts.existsById(accountId);
    }
}
