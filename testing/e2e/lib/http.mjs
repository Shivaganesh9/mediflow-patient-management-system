/**
 * Minimal HTTP helpers for the cross-service E2E journeys.
 *
 * Node's global fetch is used (Node 20+), so the suite has no dependency to install. Every helper
 * returns the parsed envelope and never logs a response body: auth responses contain tokens.
 */

/** Error carrying the HTTP status so journeys can assert expected failures precisely. */
export class HttpAssertionError extends Error {
  constructor(message, { status, body } = {}) {
    super(message);
    this.name = 'HttpAssertionError';
    this.status = status;
    this.body = body;
  }
}

export async function httpRequest(baseUrl, method, path, options = {}) {
  const { token, body, headers = {}, timeoutMs = 20_000 } = options;
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const response = await fetch(new URL(path, baseUrl), {
      method,
      headers: {
        accept: 'application/json',
        ...(body === undefined ? {} : { 'content-type': 'application/json' }),
        ...(token ? { authorization: `Bearer ${token}` } : {}),
        ...headers,
      },
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: controller.signal,
    });
    const text = await response.text();
    let json = null;
    if (text) {
      try {
        json = JSON.parse(text);
      } catch {
        json = null;
      }
    }
    return { status: response.status, headers: response.headers, text, json };
  } finally {
    clearTimeout(timer);
  }
}

/** Short, token-free description of a response for assertion messages. */
export function describeResponse(response) {
  const message = response.json?.message ? ` message="${response.json.message}"` : '';
  return `HTTP ${response.status}${message}`;
}

export function expectStatus(response, expected, label) {
  const allowed = Array.isArray(expected) ? expected : [expected];
  if (!allowed.includes(response.status)) {
    throw new HttpAssertionError(
      `${label}: expected HTTP ${allowed.join(' or ')}, received ${describeResponse(response)}`,
      { status: response.status, body: response.json },
    );
  }
  return response;
}

export function expectStatusIn(response, expected, label) {
  return expectStatus(response, expected, label);
}

export function expectOk(response, label) {
  return expectStatus(response, 200, label);
}

/** Asserts an ApiResponse envelope carries `success: true` and a data payload. */
export function unwrap(response, label) {
  if (response.json?.success !== true) {
    throw new HttpAssertionError(
      `${label}: response is not a successful ApiResponse (${describeResponse(response)})`,
      { status: response.status, body: response.json },
    );
  }
  return response.json.data;
}

export function assert(condition, message) {
  if (!condition) throw new HttpAssertionError(message);
}

export function assertEqual(actual, expected, message) {
  if (actual !== expected) {
    throw new HttpAssertionError(`${message} (expected ${JSON.stringify(expected)}, received ${JSON.stringify(actual)})`);
  }
}

export function assertMatch(value, pattern, message) {
  if (!pattern.test(String(value ?? ''))) {
    throw new HttpAssertionError(`${message} (received ${JSON.stringify(value)})`);
  }
}

export async function login(gateway, username, password) {
  const response = await httpRequest(gateway, 'POST', '/api/v1/auth/login', {
    body: { usernameOrEmail: username, password },
  });
  expectStatus(response, 200, `login ${username}`);
  const data = unwrap(response, `login ${username}`);
  if (!data?.accessToken) {
    throw new HttpAssertionError(`login ${username}: response did not include an access token`);
  }
  return {
    username,
    accessToken: data.accessToken,
    refreshToken: data.refreshToken,
    role: Array.isArray(data.roles) ? data.roles[0] : data.role,
  };
}

/** Polls `probe` until it returns a truthy value or the timeout elapses. */
export async function waitFor(description, probe, { timeoutMs = 60_000, intervalMs = 1_000 } = {}) {
  const deadline = Date.now() + timeoutMs;
  let lastError = null;
  while (Date.now() < deadline) {
    try {
      const value = await probe();
      if (value) return value;
    } catch (error) {
      lastError = error;
    }
    await new Promise((resolve) => setTimeout(resolve, intervalMs));
  }
  const suffix = lastError ? `; last error: ${lastError.message}` : '';
  throw new HttpAssertionError(`timed out after ${timeoutMs}ms waiting for ${description}${suffix}`);
}
