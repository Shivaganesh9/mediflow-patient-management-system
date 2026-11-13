-- Milestone 3: authentication and identity lifecycle.
-- Additive only: existing rows and columns are preserved.

-- ---------------------------------------------------------------------------
-- Login throttling state on users
-- ---------------------------------------------------------------------------
ALTER TABLE users
    ADD COLUMN failed_login_attempts INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN locked_until TIMESTAMP,
    ADD COLUMN last_failed_login_at TIMESTAMP,
    ADD COLUMN password_changed_at TIMESTAMP;

-- ---------------------------------------------------------------------------
-- Refresh-token families, rotation and reuse detection
-- ---------------------------------------------------------------------------
ALTER TABLE refresh_tokens
    ADD COLUMN jti VARCHAR(64),
    ADD COLUMN family_id UUID,
    ADD COLUMN replaced_by_jti VARCHAR(64),
    ADD COLUMN revoked_at TIMESTAMP,
    ADD COLUMN revoke_reason VARCHAR(40);

-- Existing rows predate hashed lookup: give them a synthetic jti/family so the
-- NOT NULL constraints can be applied. They will no longer be returned by the
-- new SHA-256 lookup, so users with an old refresh token simply sign in again.
UPDATE refresh_tokens SET jti = id::text WHERE jti IS NULL;
UPDATE refresh_tokens SET family_id = id WHERE family_id IS NULL;
UPDATE refresh_tokens SET revoked_at = created_at WHERE revoked = TRUE AND revoked_at IS NULL;

ALTER TABLE refresh_tokens ALTER COLUMN jti SET NOT NULL;
ALTER TABLE refresh_tokens ALTER COLUMN family_id SET NOT NULL;

CREATE UNIQUE INDEX uk_refresh_tokens_jti ON refresh_tokens (jti);
CREATE INDEX idx_refresh_tokens_family ON refresh_tokens (family_id);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);

-- ---------------------------------------------------------------------------
-- Password reset tokens (hash only - the raw token is never stored)
-- ---------------------------------------------------------------------------
CREATE TABLE password_reset_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP,
    requested_ip VARCHAR(64),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    deleted_at TIMESTAMP,
    CONSTRAINT fk_password_reset_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_password_reset_user ON password_reset_tokens (user_id);
CREATE INDEX idx_password_reset_expiry ON password_reset_tokens (expires_at);

-- ---------------------------------------------------------------------------
-- Security audit trail
-- ---------------------------------------------------------------------------
CREATE TABLE auth_audit_events (
    id UUID PRIMARY KEY,
    user_id UUID,
    username VARCHAR(100),
    event_type VARCHAR(60) NOT NULL,
    outcome VARCHAR(20) NOT NULL,
    ip_address VARCHAR(64),
    user_agent VARCHAR(255),
    detail VARCHAR(500),
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_auth_audit_user ON auth_audit_events (user_id);
CREATE INDEX idx_auth_audit_created ON auth_audit_events (created_at);
CREATE INDEX idx_auth_audit_event_type ON auth_audit_events (event_type);
