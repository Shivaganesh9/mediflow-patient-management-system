/**
 * HS256 JWT minting for negative tests only.
 *
 * The E2E run generates the `JWT_SECRET` it gives the services, so it can mint a token that a
 * service's normal user-token parser accepts. That is exactly what the internal-endpoint test
 * needs: a *valid* user credential (or a token that merely claims `type=service`) must still be
 * rejected by the doctor-service internal API, because service credentials are signed with a
 * different, derived key.
 */
import { createHmac } from 'node:crypto';

function encode(value) {
  return Buffer.from(JSON.stringify(value)).toString('base64url');
}

export function signHs256(payload, secret) {
  const header = encode({ alg: 'HS256', typ: 'JWT' });
  const body = encode(payload);
  const signature = createHmac('sha256', secret).update(`${header}.${body}`).digest('base64url');
  return `${header}.${body}.${signature}`;
}

/** A bearer token signed with the raw `JWT_SECRET`, i.e. a *user* credential. */
export function userToken(secret, { username, userId, roles, ttlSeconds = 600 }) {
  const now = Math.floor(Date.now() / 1000);
  return signHs256(
    {
      sub: username,
      uid: userId,
      roles,
      iss: 'auth-service',
      iat: now,
      exp: now + ttlSeconds,
    },
    secret,
  );
}

/**
 * A token that copies the service credential's claims but is signed with the raw JWT secret.
 * It must never authenticate the internal endpoint.
 */
export function fakeServiceToken(secret, { ttlSeconds = 120 } = {}) {
  const now = Math.floor(Date.now() / 1000);
  return signHs256(
    {
      sub: 'appointment-service',
      iss: 'appointment-service',
      aud: 'doctor-service',
      type: 'service',
      scope: 'doctor-schedule:validate',
      iat: now,
      exp: now + ttlSeconds,
    },
    secret,
  );
}
