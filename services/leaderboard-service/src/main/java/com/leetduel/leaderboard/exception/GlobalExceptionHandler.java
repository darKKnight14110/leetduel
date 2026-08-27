package com.leetduel.leaderboard.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

// Same {"error": "..."} shape as every other service's GlobalExceptionHandler
// in this repo (user-service, duel-service, matchmaking-service) - the
// frontend's extractMessage() in lib/api.ts already knows this shape.
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UserNotRankedException.class)
    public ResponseEntity<Map<String, String>> handleNotRanked(UserNotRankedException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
    }
}
