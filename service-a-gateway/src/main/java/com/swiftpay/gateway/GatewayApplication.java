package com.swiftpay.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

// SwiftPay Service A — Transaction Gateway. Accepts payments, persists PENDING, emits PaymentInitiated.
@SpringBootApplication
public class GatewayApplication {
    public static void main(String[] args) {
        // Run the whole service in UTC (best practice for money/ledgers) — also avoids the JVM's
        // legacy "Asia/Calcutta" tz name that Postgres rejects.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(GatewayApplication.class, args);
    }
}
