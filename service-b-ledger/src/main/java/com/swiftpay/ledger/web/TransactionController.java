package com.swiftpay.ledger.web;

import com.swiftpay.ledger.payment.Transaction;
import com.swiftpay.ledger.payment.TransactionRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Reporting endpoint: a user's transaction history (as sender or receiver).
@RestController
@RequestMapping("/v1/users")
public class TransactionController {

    private final TransactionRepository txRepo;

    public TransactionController(TransactionRepository txRepo) {
        this.txRepo = txRepo;
    }

    @GetMapping("/{id}/transactions")
    public List<Transaction> history(@PathVariable Long id) {
        return txRepo.findBySenderIdOrReceiverIdOrderByCreatedAtDesc(id, id);
    }
}
