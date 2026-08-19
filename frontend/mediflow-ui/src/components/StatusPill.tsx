const toneForStatus: Record<string, string> = {
  PAID: 'success',
  COMPLETED: 'success',
  CONFIRMED: 'info',
  IN_PROGRESS: 'info',
  SCHEDULED: 'warning',
  PENDING: 'warning',
  PARTIALLY_PAID: 'warning',
  CANCELLED: 'danger',
  REFUNDED: 'danger',
  NO_SHOW: 'danger',
};

export function StatusPill({ status }: { status: string }) {
  const tone = toneForStatus[status] ?? 'neutral';
  const label = status.replace(/_/g, ' ');
  return <span className={`pill pill-${tone}`}>{label}</span>;
}