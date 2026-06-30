package com.swiftpay.gateway.payment.controller;

import com.swiftpay.gateway.entity.Transaction;
import com.swiftpay.gateway.payment.dto.PaymentRequest;
import com.swiftpay.gateway.payment.dto.PaymentResponse;
import com.swiftpay.gateway.payment.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
public class PaymentController {

    private final PaymentService service;

    public PaymentController(PaymentService service) {
        this.service = service;
    }

    // POST /v1/payments — accept a payment; returns 202 because settlement happens asynchronously in the ledger.
    @PostMapping("/v1/payments")
    public ResponseEntity<PaymentResponse> create(@Valid @RequestBody PaymentRequest req) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(service.initiate(req));
    }

    // GET /v1/payments/{id} — poll the current status of a transaction.
    @GetMapping("/v1/payments/{id}")
    public PaymentResponse status(@PathVariable UUID id) {
        Transaction t = service.getStatus(id);
        return new PaymentResponse(t.getId(), t.getStatus(),
                t.getFailureReason() != null ? t.getFailureReason() : "ok");
    }

    // GET /v1/users/{id}/transactions — a user's transaction history.
    @GetMapping("/v1/users/{id}/transactions")
    public List<Transaction> history(@PathVariable Long id) {
        return service.history(id);
    }
}
