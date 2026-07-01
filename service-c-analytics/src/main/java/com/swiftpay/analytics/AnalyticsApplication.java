package com.swiftpay.analytics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class AnalyticsApplication {

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));   // a financial app should think in UTC
        SpringApplication.run(AnalyticsApplication.class, args);
    }
}
