package com.swiftpay.ledger;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

// SwiftPay Service B — Ledger. Consumes PaymentInitiated, performs the atomic transfer, settles the payment.
@SpringBootApplication
public class LedgerApplication {
    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC")); // money/ledger runs in UTC (also avoids the Asia/Calcutta tz issue)
        SpringApplication.run(LedgerApplication.class, args);
    }
}
