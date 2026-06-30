package com.swiftpay.ledger.repository;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

// Runs the real atomic debit/credit queries against a REAL Postgres (via Testcontainers).
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)   // runs in CI; skips cleanly where Docker isn't reachable
class AccountRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        // let Hibernate build the schema from the entities for the test (prod uses db/init.sql)
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired AccountRepository accounts;
    @Autowired EntityManager em;

    @BeforeEach
    void seed() {
        em.createNativeQuery("insert into accounts(id, name, balance, currency, updated_at) " +
                "values (1, 'Alice', 1000.00, 'INR', now())").executeUpdate();
        em.flush();
        em.clear();
    }

    @Test
    void debitSucceedsWhenFundsAreSufficient() {
        int rows = accounts.debit(1L, new BigDecimal("100.00"));
        em.flush();
        em.clear();

        assertThat(rows).isEqualTo(1);
        assertThat(accounts.findById(1L).orElseThrow().getBalance()).isEqualByComparingTo("900.00");
    }

    @Test
    void debitFailsAndNeverOverdraws() {
        int rows = accounts.debit(1L, new BigDecimal("5000.00"));   // more than the balance
        em.flush();
        em.clear();

        assertThat(rows).isZero();   // the WHERE balance >= amount guard rejected it
        assertThat(accounts.findById(1L).orElseThrow().getBalance()).isEqualByComparingTo("1000.00");
    }

    @Test
    void creditIncreasesTheBalance() {
        int rows = accounts.credit(1L, new BigDecimal("250.00"));
        em.flush();
        em.clear();

        assertThat(rows).isEqualTo(1);
        assertThat(accounts.findById(1L).orElseThrow().getBalance()).isEqualByComparingTo("1250.00");
    }
}
