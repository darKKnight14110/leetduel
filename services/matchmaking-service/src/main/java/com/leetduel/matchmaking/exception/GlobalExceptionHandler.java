package com.leetduel.matchmaking.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // 404, retryable by the client shortly - see ProfileNotFoundException's
    // own comment for why this is distinct from a 502.
    @ExceptionHandler(ProfileNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleProfileNotFound(ProfileNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
    }

    // 502, not 500 - the failure is a downstream dependency (User Service or
    // Problem Service) being unreachable, not a bug in this service.
    @ExceptionHandler({UserServiceUnavailableException.class, ProblemServiceUnavailableException.class})
    public ResponseEntity<Map<String, String>> handleUpstreamUnavailable(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", ex.getMessage()));
    }

    // 503 - the broker itself is unreachable, distinct from an upstream
    // service being down.
    @ExceptionHandler(QueuePublishUnavailableException.class)
    public ResponseEntity<Map<String, String>> handleQueueUnavailable(QueuePublishUnavailableException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("error", ex.getMessage()));
    }
}
