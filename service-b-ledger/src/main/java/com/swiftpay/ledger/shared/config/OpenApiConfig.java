package com.swiftpay.ledger.shared.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Titles the auto-generated Swagger UI (served at /swagger-ui.html).
@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI swiftpayOpenApi() {
        return new OpenAPI().info(new Info()
                .title("SwiftPay — Ledger Service")
                .version("v1")
                .description("Internal worker: consumes PaymentInitiated and performs the atomic transfer. Exposes only a health check."));
    }
}
