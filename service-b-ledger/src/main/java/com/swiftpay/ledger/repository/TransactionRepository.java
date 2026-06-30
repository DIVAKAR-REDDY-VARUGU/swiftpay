package com.swiftpay.ledger.repository;

import com.swiftpay.ledger.entity.Transaction;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, String> {

    // SELECT ... FOR UPDATE: locks the transaction row so duplicate/concurrent deliveries of the SAME
    // payment serialize here. This makes settlement idempotent at the data layer, not by relying on a
    // single consumer thread — the second delivery waits, then sees status != PENDING and skips.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Transaction t where t.id = :id")
    Optional<Transaction> findByIdForUpdate(@Param("id") String id);
}
