export interface ApiEnvelope<T> {
  success: boolean;
  message: string;
  data?: T;
  errors?: Record<string, string>;
  timestamp: string;
}

export interface Page<T> {
  content: T[];
  totalPages: number;
  totalElements: number;
  last: boolean;
  first: boolean;
  numberOfElements: number;
  size: number;
  number: number;
  empty: boolean;
}

export class ApiError extends Error {
  readonly status: number;
  readonly errors?: Record<string, string>;
  /**
   * Correlation id of the failed logical request. It is safe to show to a user: the gateway only
   * accepts/echoes 8-64 characters from `[A-Za-z0-9._-]`, and it never contains credentials or
   * patient data. Support can use it to find the matching server logs.
   */
  readonly correlationId?: string;

  constructor(
    message: string,
    status: number,
    errors?: Record<string, string>,
    correlationId?: string,
  ) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.errors = errors;
    this.correlationId = correlationId;
  }
}

export function isApiError(err: unknown): err is ApiError {
  return err instanceof ApiError;
}