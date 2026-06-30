package com.swiftpay.gateway.account.service;

import com.swiftpay.gateway.entity.Account;
import com.swiftpay.gateway.repository.AccountRepository;
import com.swiftpay.gateway.shared.exception.NotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

// Account read logic. Keeps query code out of the controller (separation of layers).
@Service
public class AccountService {

    private final AccountRepository accounts;

    public AccountService(AccountRepository accounts) {
        this.accounts = accounts;
    }

    // all accounts with balances
    public List<Account> allAccounts() {
        return accounts.findAll();
    }

    // one account's balance
    public Account account(Long id) {
        return accounts.findById(id)
                .orElseThrow(() -> new NotFoundException("account " + id + " not found"));
    }
}
