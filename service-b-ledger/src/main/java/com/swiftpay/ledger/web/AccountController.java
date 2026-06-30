package com.swiftpay.ledger.web;

import com.swiftpay.ledger.account.Account;
import com.swiftpay.ledger.account.AccountRepository;
import com.swiftpay.ledger.common.NotFoundException;
import org.springframework.web.bind.annotation.*;

// Helper endpoint to inspect a balance (handy for verifying transfers).
@RestController
@RequestMapping("/v1/accounts")
public class AccountController {

    private final AccountRepository accounts;

    public AccountController(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @GetMapping("/{id}")
    public Account get(@PathVariable Long id) {
        return accounts.findById(id)
                .orElseThrow(() -> new NotFoundException("account " + id + " not found"));
    }
}
