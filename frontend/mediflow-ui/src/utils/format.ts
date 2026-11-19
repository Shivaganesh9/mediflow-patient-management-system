export function formatDateTime(value: string | null | undefined): string {
  if (!value) return '—';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString(undefined, {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

export function formatDate(value: string | null | undefined): string {
  if (!value) return '—';
  const date = new Date(`${value}T00:00:00`);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' });
}

export function formatLocalTime(value: string | null | undefined): string {
  return value ? value.slice(0, 5) : '—';
}

export function formatCurrency(value: number | string | null | undefined, currency = 'USD'): string {
  const amount = Number(value ?? 0);
  try {
    return new Intl.NumberFormat(undefined, { style: 'currency', currency }).format(amount);
  } catch {
    return `${currency} ${amount.toFixed(2)}`;
  }
}

export function formatMoney(value: number | string | null | undefined): string {
  return formatCurrency(value);
}

export function toLocalDateTimeInput(value: string): string {
  return value ? value.slice(0, 16) : '';
}

/**
 * Normalizes a `datetime-local` control value to the naive `YYYY-MM-DDTHH:mm:ss` form the services
 * accept. The control can yield either `HH:mm` or `HH:mm:ss`, and both the change handler and the
 * submit handler call this helper, so it must be idempotent.
 */
export function fromLocalDateTimeInput(value: string): string {
  return value ? `${value.slice(0, 16)}:00` : '';
}

export function displayName(firstName?: string, lastName?: string): string {
  if (firstName && lastName) return `${firstName} ${lastName}`;
  return firstName ?? lastName ?? '—';
}

export function shortId(value: string | null | undefined): string {
  if (!value) return '—';
  return value.length > 12 ? `${value.slice(0, 12)}…` : value;
}