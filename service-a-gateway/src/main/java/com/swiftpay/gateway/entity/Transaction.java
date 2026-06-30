package com.swiftpay.gateway.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

// Maps the "transactions" table. The id is the transaction_id AND the idempotency key (a client-supplied string).
@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    private String id;

    @Column(name = "sender_id")
    private Long senderId;

    @Column(name = "receiver_id")
    private Long receiverId;

    private BigDecimal amount;
    private String currency;

    @Enumerated(EnumType.STRING)
    private TransactionStatus status;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected Transaction() {}

    public Transaction(String id, Long senderId, Long receiverId, BigDecimal amount, String currency, TransactionStatus status) {
        this.id = id;
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public String getId() { return id; }
    public Long getSenderId() { return senderId; }
    public Long getReceiverId() { return receiverId; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public TransactionStatus getStatus() { return status; }
    public String getFailureReason() { return failureReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
