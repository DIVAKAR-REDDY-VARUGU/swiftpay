package com.swiftpay.ledger.health.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

// The Ledger is a background Kafka consumer; the only HTTP endpoint it exposes is this health check.
@RestController
public class HealthController {

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "service", "ledger");
    }
}
