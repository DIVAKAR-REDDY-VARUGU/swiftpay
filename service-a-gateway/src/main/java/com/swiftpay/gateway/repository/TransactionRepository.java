package com.swiftpay.gateway.repository;

import com.swiftpay.gateway.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, String> {

    // a user's history = any transaction where they are the sender OR the receiver
    List<Transaction> findBySenderIdOrReceiverIdOrderByCreatedAtDesc(Long senderId, Long receiverId);
}
