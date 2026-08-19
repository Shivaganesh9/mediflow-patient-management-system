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

  constructor(message: string, status: number, errors?: Record<string, string>) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.errors = errors;
  }
}

export function isApiError(err: unknown): err is ApiError {
  return err instanceof ApiError;
}