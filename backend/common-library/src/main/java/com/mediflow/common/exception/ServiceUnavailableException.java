package com.mediflow.common.exception;

/**
 * Raised when a required downstream dependency (e.g. another service) cannot be reached
 * or answers with an unexpected failure. Maps to HTTP 503 so callers can retry, unlike
 * a 500 which signals a bug in this service.
 */
public class ServiceUnavailableException extends RuntimeException {

    public ServiceUnavailableException(String message) {
        super(message);
    }

    public ServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
