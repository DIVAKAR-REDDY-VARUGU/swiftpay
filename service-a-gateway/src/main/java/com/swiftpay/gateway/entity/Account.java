package com.swiftpay.gateway.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

// Maps the "accounts" table. The gateway reads balances here for a pre-check and for reporting.
@Entity
@Table(name = "accounts")
public class Account {

    @Id
    private Long id;
    private String name;
    private BigDecimal balance;
    private String currency;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected Account() {}

    public Long getId() { return id; }
    public String getName() { return name; }
    public BigDecimal getBalance() { return balance; }
    public String getCurrency() { return currency; }
    public Instant getUpdatedAt() { return updatedAt; }
}
