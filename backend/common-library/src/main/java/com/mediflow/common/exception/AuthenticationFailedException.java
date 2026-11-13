package com.mediflow.common.exception;

/**
 * Generic authentication failure. The message is deliberately vague so callers cannot
 * distinguish "unknown user" from "wrong password" (account enumeration protection).
 * Maps to HTTP 401.
 */
public class AuthenticationFailedException extends RuntimeException {

    public AuthenticationFailedException(String message) {
        super(message);
    }

    public AuthenticationFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
