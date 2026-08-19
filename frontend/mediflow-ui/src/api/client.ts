import { ApiEnvelope, ApiError } from '../types/common';

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';

const ACCESS_TOKEN_KEY = 'mediflow.accessToken';
const REFRESH_TOKEN_KEY = 'mediflow.refreshToken';

export function getAccessToken(): string | null {
  return localStorage.getItem(ACCESS_TOKEN_KEY);
}

export function getRefreshToken(): string | null {
  return localStorage.getItem(REFRESH_TOKEN_KEY);
}

export function setTokens(accessToken: string, refreshToken: string): void {
  localStorage.setItem(ACCESS_TOKEN_KEY, accessToken);
  localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
}

export function clearSession(): void {
  localStorage.removeItem(ACCESS_TOKEN_KEY);
  localStorage.removeItem(REFRESH_TOKEN_KEY);
  localStorage.removeItem('mediflow.user');
}

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  body?: unknown;
  params?: object;
  auth?: boolean;
}

let refreshPromise: Promise<string | null> | null = null;

async function refreshAccessToken(): Promise<string | null> {
  const refreshToken = getRefreshToken();
  if (!refreshToken) return null;
  if (!refreshPromise) {
    refreshPromise = (async () => {
      try {
        const res = await fetch(`${BASE_URL}/api/v1/auth/refresh`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ refreshToken }),
        });
        if (!res.ok) return null;
        const envelope = (await res.json()) as ApiEnvelope<{
          accessToken: string;
          refreshToken: string;
        }>;
        if (!envelope.data?.accessToken) return null;
        setTokens(envelope.data.accessToken, envelope.data.refreshToken);
        return envelope.data.accessToken;
      } catch {
        return null;
      } finally {
        refreshPromise = null;
      }
    })();
  }
  return refreshPromise;
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
  const headers: Record<string, string> = {};
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
      clearSession();
      window.dispatchEvent(new Event('mediflow:unauthorized'));
    }
    throw new ApiError(envelope?.message ?? 'Request failed', res.status, envelope?.errors);
  }
  return envelope?.data as T;
}

export async function api<T>(path: string, options: RequestOptions = {}): Promise<T> {
  let res = await performRequest(path, options);
  if (res.status === 401 && options.auth !== false && getRefreshToken()) {
    const newToken = await refreshAccessToken();
    if (newToken) res = await performRequest(path, options);
  }
  return handleResponse<T>(res);
}