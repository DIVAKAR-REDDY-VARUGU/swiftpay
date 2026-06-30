package com.swiftpay.ledger.account;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;

public interface AccountRepository extends JpaRepository<Account, Long> {

    // ATOMIC, RACE-SAFE DEBIT: the balance check IS the update. Returns 1 if debited, 0 if insufficient funds.
    // Two concurrent payments can never overdraw, because only one UPDATE can satisfy "balance >= amount".
    @Modifying
    @Query("update Account a set a.balance = a.balance - :amt where a.id = :id and a.balance >= :amt")
    int debit(@Param("id") Long id, @Param("amt") BigDecimal amt);

    @Modifying
    @Query("update Account a set a.balance = a.balance + :amt where a.id = :id")
    int credit(@Param("id") Long id, @Param("amt") BigDecimal amt);
}
