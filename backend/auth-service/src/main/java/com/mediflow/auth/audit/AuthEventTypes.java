package com.mediflow.auth.audit;

/** Canonical audit event names (stored in {@code auth_audit_events.event_type}). */
public final class AuthEventTypes {

    public static final String REGISTERED = "REGISTERED";
    public static final String LOGIN_SUCCEEDED = "LOGIN_SUCCEEDED";
    public static final String LOGIN_FAILED = "LOGIN_FAILED";
    public static final String LOGIN_BLOCKED = "LOGIN_BLOCKED";
    public static final String LOGIN_LOCKED_OUT = "LOGIN_LOCKED_OUT";
    public static final String LOGOUT = "LOGOUT";
    public static final String TOKEN_REFRESHED = "TOKEN_REFRESHED";
    public static final String REFRESH_REJECTED = "REFRESH_REJECTED";
    public static final String REFRESH_REUSE_DETECTED = "REFRESH_REUSE_DETECTED";
    public static final String PASSWORD_CHANGED = "PASSWORD_CHANGED";
    public static final String PASSWORD_RESET_REQUESTED = "PASSWORD_RESET_REQUESTED";
    public static final String PASSWORD_RESET_COMPLETED = "PASSWORD_RESET_COMPLETED";
    public static final String ACCOUNT_STATUS_CHANGED = "ACCOUNT_STATUS_CHANGED";
    public static final String ACCOUNT_ROLES_CHANGED = "ACCOUNT_ROLES_CHANGED";
    public static final String ADMIN_BOOTSTRAPPED = "ADMIN_BOOTSTRAPPED";

    private AuthEventTypes() {
    }
}
