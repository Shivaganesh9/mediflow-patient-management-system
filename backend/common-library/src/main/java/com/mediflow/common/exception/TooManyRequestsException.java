package com.mediflow.common.exception;

/**
 * Temporary throttling response, used for login lockout. Maps to HTTP 429 with a
 * {@code Retry-After} header. The lockout is always time-based, so an attacker cannot
 * permanently deny access to a known account.
 */
public class TooManyRequestsException extends RuntimeException {

    private final long retryAfterSeconds;

    public TooManyRequestsException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
