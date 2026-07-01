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

// the debit/credit are raw sql queries, so mocks cant really test them - i need a real postgres.
// testcontainers spins up an actual postgres in a container just for this test. it skips itself
// if docker isnt running (like on my machine), and runs for real on the ci server.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class AccountRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        // for the test i let hibernate build the tables from the entities. the real app uses db/init.sql instead.
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired AccountRepository accounts;
    @Autowired EntityManager em;

    @BeforeEach
    void seed() {
        // start every test with one account: id 1, balance 1000
        em.createNativeQuery("insert into accounts(id, name, balance, currency, updated_at) " +
                "values (1, 'Alice', 1000.00, 'INR', now())").executeUpdate();
        em.flush();
        em.clear();
    }

    @Test
    void debitSucceedsWhenFundsAreSufficient() {
        // enough money in the account, so the debit should apply (returns 1) and the balance drops by 100
        int rows = accounts.debit(1L, new BigDecimal("100.00"));
        em.flush();
        em.clear();

        assertThat(rows).isEqualTo(1);
        assertThat(accounts.findById(1L).orElseThrow().getBalance()).isEqualByComparingTo("900.00");
    }

    @Test
    void debitFailsAndNeverOverdraws() {
        // this is the important one. trying to pull out more than whats in the account.
        // the "where balance >= amount" in the query should reject it (returns 0) and leave the balance alone.
        // thats what stops two payments from overdrawing the same account.
        int rows = accounts.debit(1L, new BigDecimal("5000.00"));
        em.flush();
        em.clear();

        assertThat(rows).isZero();
        assertThat(accounts.findById(1L).orElseThrow().getBalance()).isEqualByComparingTo("1000.00");
    }

    @Test
    void creditIncreasesTheBalance() {
        // credit just adds money to the receiver, no guard needed. quick sanity check it works.
        int rows = accounts.credit(1L, new BigDecimal("250.00"));
        em.flush();
        em.clear();

        assertThat(rows).isEqualTo(1);
        assertThat(accounts.findById(1L).orElseThrow().getBalance()).isEqualByComparingTo("1250.00");
    }
}
