import { ApiEnvelope, ApiError } from '../types/common';

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';

const ACCESS_TOKEN_KEY = 'mediflow.accessToken';
const REFRESH_TOKEN_KEY = 'mediflow.refreshToken';
const ACCESS_EXPIRY_KEY = 'mediflow.accessExpiresAt';
const REFRESH_LOCK_KEY = 'mediflow.refreshLock';
const USER_KEY = 'mediflow.user';

/** Refresh slightly before the access token expires so requests never race the deadline. */
const EXPIRY_SKEW_MS = 30_000;
/** Cross-tab refresh lock lifetime; guards against a tab dying mid-refresh. */
const REFRESH_LOCK_TTL_MS = 10_000;
const REFRESH_LOCK_POLL_MS = 120;

const TAB_ID = `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}`;

/** Reasons the client-side session may end; surfaced to the user on the login screen. */
export type SessionEndReason = 'expired' | 'signed-out';

/** Emitted whenever the client drops a session so the UI can explain what happened. */
export const SESSION_ENDED_EVENT = 'mediflow:session-ended';

export function getAccessToken(): string | null {
  return localStorage.getItem(ACCESS_TOKEN_KEY);
}

export function getRefreshToken(): string | null {
  return localStorage.getItem(REFRESH_TOKEN_KEY);
}

/** True when the browser still holds something that can re-establish the session. */
export function hasSession(): boolean {
  return Boolean(getAccessToken() ?? getRefreshToken());
}

export function setTokens(accessToken: string, refreshToken: string, expiresInSeconds?: number): void {
  localStorage.setItem(ACCESS_TOKEN_KEY, accessToken);
  localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);

  const expiresAt = expiresInSeconds && Number.isFinite(expiresInSeconds)
    ? Date.now() + expiresInSeconds * 1000
    : jwtExpiryMillis(accessToken);
  if (expiresAt) {
    localStorage.setItem(ACCESS_EXPIRY_KEY, String(expiresAt));
  } else {
    localStorage.removeItem(ACCESS_EXPIRY_KEY);
  }
}

export function clearSession(): void {
  localStorage.removeItem(ACCESS_TOKEN_KEY);
  localStorage.removeItem(REFRESH_TOKEN_KEY);
  localStorage.removeItem(ACCESS_EXPIRY_KEY);
  localStorage.removeItem(REFRESH_LOCK_KEY);
  localStorage.removeItem(USER_KEY);
}

/** Announces that the session is over, so AuthContext can show the right message. */
export function emitSessionEnded(reason: SessionEndReason): void {
  window.dispatchEvent(new CustomEvent<{ reason: SessionEndReason }>(SESSION_ENDED_EVENT, { detail: { reason } }));
}

/**
 * Drops the local session and, if there was one, tells the UI it expired. Used for a failed
 * refresh as well as a 401, so reloading with a dead refresh token still explains the logout.
 */
function endSession(): void {
  const hadSession = hasSession();
  clearSession();
  if (hadSession) emitSessionEnded('expired');
}

/** True when the access token is missing, unknown or about to expire. */
export function shouldRefreshAccessToken(): boolean {
  const token = getAccessToken();
  if (!token) return Boolean(getRefreshToken());
  const expiresAt = storedExpiryMillis() ?? jwtExpiryMillis(token);
  if (!expiresAt) return false;
  return expiresAt - Date.now() <= EXPIRY_SKEW_MS;
}

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  body?: unknown;
  params?: object;
  /** Extra request headers, e.g. a caller-supplied Idempotency-Key. */
  headers?: Record<string, string>;
  /** Set to false for public endpoints (login, register, password reset). */
  auth?: boolean;
}

let refreshPromise: Promise<string | null> | null = null;

/**
 * Refreshes the session at most once per browser tab. The cross-tab lock makes sure two
 * tabs never rotate the same refresh token concurrently, which the server treats as token
 * theft. A tab that loses the race simply adopts the tokens written by the winner.
 */
async function refreshAccessToken(): Promise<string | null> {
  if (refreshPromise) return refreshPromise;

  refreshPromise = (async () => {
    try {
      const deadline = Date.now() + REFRESH_LOCK_TTL_MS + 2_000;
      while (!acquireRefreshLock()) {
        await wait(REFRESH_LOCK_POLL_MS);
        const token = getAccessToken();
        if (token && !shouldRefreshAccessToken()) return token;
        if (Date.now() > deadline) break;
      }

      // Re-read after taking the lock: another tab may have rotated the token meanwhile.
      const refreshToken = getRefreshToken();
      if (!refreshToken) return null;

      try {
        const res = await fetch(`${BASE_URL}/api/v1/auth/refresh`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ refreshToken }),
        });
        if (!res.ok) {
          endSession();
          return null;
        }
        const envelope = (await res.json()) as ApiEnvelope<{
          accessToken: string;
          refreshToken: string;
          expiresIn?: number;
        }>;
        if (!envelope.data?.accessToken || !envelope.data.refreshToken) {
          endSession();
          return null;
        }
        setTokens(envelope.data.accessToken, envelope.data.refreshToken, envelope.data.expiresIn);
        return envelope.data.accessToken;
      } catch {
        // Network failure: keep the tokens so a later attempt can succeed.
        return null;
      }
    } finally {
      releaseRefreshLock();
      refreshPromise = null;
    }
  })();

  return refreshPromise;
}

function acquireRefreshLock(): boolean {
  const now = Date.now();
  const current = readRefreshLock();
  if (current && current.owner !== TAB_ID && current.expiresAt > now) return false;
  localStorage.setItem(REFRESH_LOCK_KEY, JSON.stringify({ owner: TAB_ID, expiresAt: now + REFRESH_LOCK_TTL_MS }));
  return true;
}

function releaseRefreshLock(): void {
  const current = readRefreshLock();
  if (current && current.owner === TAB_ID) localStorage.removeItem(REFRESH_LOCK_KEY);
}

function readRefreshLock(): { owner: string; expiresAt: number } | null {
  const raw = localStorage.getItem(REFRESH_LOCK_KEY);
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw) as { owner?: unknown; expiresAt?: unknown };
    if (typeof parsed.owner !== 'string' || typeof parsed.expiresAt !== 'number') return null;
    return { owner: parsed.owner, expiresAt: parsed.expiresAt };
  } catch {
    return null;
  }
}

function storedExpiryMillis(): number | null {
  const raw = localStorage.getItem(ACCESS_EXPIRY_KEY);
  if (!raw) return null;
  const value = Number(raw);
  return Number.isFinite(value) ? value : null;
}

function jwtExpiryMillis(token: string): number | null {
  try {
    const payload = token.split('.')[1];
    if (!payload) return null;
    const normalized = payload.replace(/-/g, '+').replace(/_/g, '/');
    const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, '=');
    const claims = JSON.parse(atob(padded)) as { exp?: unknown };
    return typeof claims.exp === 'number' ? claims.exp * 1000 : null;
  } catch {
    return null;
  }
}

function wait(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function buildUrl(path: string, params?: RequestOptions['params']): string {
  const url = new URL(`${BASE_URL}${path}`);
  if (params) {
    Object.entries(params).forEach(([key, value]) => {
      if (value !== undefined && value !== '') url.searchParams.set(key, String(value));
    });
  }
  return url.toString();
}

async function performRequest(path: string, options: RequestOptions): Promise<Response> {
  const headers: Record<string, string> = { ...(options.headers ?? {}) };
  if (options.body !== undefined) headers['Content-Type'] = 'application/json';
  const token = getAccessToken();
  if (token) headers['Authorization'] = `Bearer ${token}`;
  return fetch(buildUrl(path, options.params), {
    method: options.method ?? 'GET',
    headers,
    body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
  });
}

async function handleResponse<T>(res: Response): Promise<T> {
  if (res.status === 204) return undefined as T;
  const text = await res.text();
  let envelope: ApiEnvelope<T> | null = null;
  try {
    envelope = text ? (JSON.parse(text) as ApiEnvelope<T>) : null;
  } catch {
    envelope = null;
  }
  if (!res.ok) {
    if (res.status === 401) {
      endSession();
    }
    throw new ApiError(envelope?.message ?? 'Request failed', res.status, envelope?.errors);
  }
  return envelope?.data as T;
}

export async function api<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const authenticated = options.auth !== false;

  // Proactive refresh keeps long-lived tabs working and makes reload-with-expired-token work.
  if (authenticated && shouldRefreshAccessToken()) {
    await refreshAccessToken();
  }

  let res = await performRequest(path, options);

  if (res.status === 401 && authenticated && getRefreshToken()) {
    const newToken = await refreshAccessToken();
    if (newToken) res = await performRequest(path, options);
  }

  return handleResponse<T>(res);
}
