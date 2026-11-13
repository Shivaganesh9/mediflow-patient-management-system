package com.mediflow.common.security;

/**
 * Raised when the shared revocation store cannot be reached and the service is
 * configured to fail closed. Authentication must not silently succeed in that
 * state, because a revoked token could be accepted during an outage.
 */
public class RevocationUnavailableException extends RuntimeException {

    public RevocationUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
