package com.swiftpay.gateway.common;

public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) { super(message); }
}
