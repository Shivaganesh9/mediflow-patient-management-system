package com.mediflow.common.exception;

import com.mediflow.common.enums.AccountStatus;

/**
 * Raised after credentials were verified but the account is not allowed to sign in
 * (suspended, deactivated, pending verification). Returning this only after a correct
 * password prevents account-status enumeration. Maps to HTTP 403.
 */
public class InactiveAccountException extends RuntimeException {

    private final AccountStatus status;

    public InactiveAccountException(AccountStatus status) {
        super(messageFor(status));
        this.status = status;
    }

    public AccountStatus getStatus() {
        return status;
    }

    private static String messageFor(AccountStatus status) {
        return switch (status) {
            case SUSPENDED -> "Account is suspended. Contact an administrator.";
            case INACTIVE -> "Account is deactivated. Contact an administrator.";
            case PENDING_VERIFICATION -> "Account is pending verification.";
            case DELETED, LOCKED -> "Account is not available. Contact an administrator.";
            default -> "Account is not active.";
        };
    }
}
