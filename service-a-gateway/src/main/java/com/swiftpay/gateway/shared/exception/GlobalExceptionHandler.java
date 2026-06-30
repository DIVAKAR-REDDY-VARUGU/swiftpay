package com.swiftpay.gateway.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

// Turns exceptions into a standard error object with the right HTTP status (API Standards requirement).
@RestControllerAdvice
public class GlobalExceptionHandler {

    public record ApiError(String timestamp, int status, String error, Object message) {}

    private ResponseEntity<ApiError> of(HttpStatus status, Object message) {
        return ResponseEntity.status(status)
                .body(new ApiError(Instant.now().toString(), status.value(), status.getReasonPhrase(), message));
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> notFound(NotFoundException e) {
        return of(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiError> badRequest(BadRequestException e) {
        return of(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ApiError> insufficientFunds(InsufficientFundsException e) {
        return of(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage()); // 422
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validation(MethodArgumentNotValidException e) {
        Map<String, String> fields = new HashMap<>();
        for (FieldError fe : e.getBindingResult().getFieldErrors()) {
            fields.put(fe.getField(), fe.getDefaultMessage());
        }
        return of(HttpStatus.BAD_REQUEST, fields);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> other(Exception e) {
        return of(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error");
    }
}
