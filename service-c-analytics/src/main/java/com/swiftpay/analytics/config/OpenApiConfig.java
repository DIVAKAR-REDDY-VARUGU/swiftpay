package com.swiftpay.analytics.config;

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
                .title("SwiftPay — Analytics Service")
                .version("v1")
                .description("Consumes payment outcomes from Kafka into ClickHouse and serves read-only analytics."));
    }
}
