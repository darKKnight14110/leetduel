package com.leetduel.wsgateway.security;

import org.springframework.messaging.MessagingException;

public class StompAuthenticationException extends MessagingException {

    public StompAuthenticationException(String message) {
        super(message);
    }
}
