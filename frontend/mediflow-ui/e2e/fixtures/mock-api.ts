import { Page } from '@playwright/test';
import { MockApi as SharedMockApi } from '../../src/demo/mock-api';

/**
 * Playwright adapter for the shared in-browser API double.
 *
 * The behaviour lives in `src/demo/mock-api.ts` so the demo mode and the browser suite exercise the
 * exact same synthetic data and response contracts. This file only bridges Playwright routes to the
 * transport-neutral `dispatch()` entry point.
 */
export {
  SEED_USERS,
  SECOND_DOCTOR_ID,
  SECOND_PATIENT_ID,
  localDateTime,
} from '../../src/demo/mock-api';
export type {
  MockRequest,
  MockResponse,
  RecordedRequest,
  Responder,
  Role,
  User,
} from '../../src/demo/mock-api';

export class MockApi extends SharedMockApi {
  /** Installs the double for every `/api/v1/**` request on the page. */
  async install(page: Page): Promise<void> {
    await page.route('**/api/v1/**', async (route) => {
      const request = route.request();
      const response = await this.dispatch({
        method: () => request.method(),
        url: () => request.url(),
        headers: () => request.headers(),
        postData: () => request.postData(),
      });
      await route.fulfill(response);
    });
  }
}
