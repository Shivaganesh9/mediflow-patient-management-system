import { MockApi } from './mock-api';

/** Emitted when the demo double replays a payment for a reused Idempotency-Key. */
export const DEMO_REPLAY_EVENT = 'mediflow:demo-replay';

/**
 * Installs the synthetic API for frontend-only demo mode.
 *
 * Every `/api/v1/**` fetch is answered in the browser by the same double the Playwright suite
 * uses, so the demo exercises the real router, forms, validation and role gating without a
 * backend. Nothing here runs in a production build: `main.tsx` only imports this module when
 * `VITE_DEMO_MODE` is set, and `vite.config.ts` refuses to build production with that flag.
 *
 * The double is intentionally stateful per page load: "Reset demo data" clears localStorage and
 * reloads, which rebuilds the seeded world.
 */
export function installDemoApi(): void {
  if (import.meta.env.VITE_DEMO_MODE !== 'true') return;
  if ((window as { __mediflowDemoApi?: unknown }).__mediflowDemoApi) return;

  const mock = new MockApi({
    latencyMs: 250,
    acceptSeededTokens: true,
    enforceSchedule: true,
    simulatePaymentTimeoutOnce: true,
  });

  const originalFetch = window.fetch.bind(window);

  window.fetch = async (input: RequestInfo | URL, init?: RequestInit): Promise<Response> => {
    const request = input instanceof Request ? input : new Request(input, init);
    const url = new URL(request.url, window.location.origin);
    if (!url.pathname.startsWith('/api/v1/')) return originalFetch(input, init);

    const headers: Record<string, string> = {};
    request.headers.forEach((value, key) => {
      headers[key] = value;
    });
    const body = request.method === 'GET' || request.method === 'HEAD' ? null : await request.clone().text();

    const response = await mock.dispatch({
      method: () => request.method,
      url: () => url.toString(),
      headers: () => headers,
      postData: () => body,
    });

    if (response.headers['Idempotent-Replay']?.toLowerCase() === 'true') {
      window.dispatchEvent(new CustomEvent(DEMO_REPLAY_EVENT, { detail: { path: url.pathname } }));
    }

    return new Response(response.status === 204 ? null : (response.body ?? null), {
      status: response.status,
      headers: response.headers,
    });
  };

  (window as { __mediflowDemoApi?: unknown }).__mediflowDemoApi = mock;
}
