package com.swiftpay.gateway.common;

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
                .title("SwiftPay — Transaction Gateway API")
                .version("v1")
                .description("Accepts P2P payments, ensures idempotency, and emits PaymentInitiated events."));
    }
}
