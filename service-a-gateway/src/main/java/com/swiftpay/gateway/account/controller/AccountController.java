package com.swiftpay.gateway.account.controller;

import com.swiftpay.gateway.account.service.AccountService;
import com.swiftpay.gateway.entity.Account;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1/accounts")
public class AccountController {

    private final AccountService service;

    public AccountController(AccountService service) {
        this.service = service;
    }

    // GET /v1/accounts — all accounts with balances.
    @GetMapping
    public List<Account> all() {
        return service.allAccounts();
    }

    // GET /v1/accounts/{id} — one account's balance.
    @GetMapping("/{id}")
    public Account get(@PathVariable Long id) {
        return service.account(id);
    }
}
