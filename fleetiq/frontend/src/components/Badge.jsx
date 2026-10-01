const TONES = {
  AVAILABLE: 'go', CONFIRMED: 'go', PAID: 'go', SUCCESS: 'go', DISPATCHED: 'go', SCHEDULED: 'go',
  ACTIVE: 'info', RENTED: 'info',
  PENDING_PAYMENT: 'warn', PENDING: 'warn', MAINTENANCE: 'warn',
  CANCELLED: 'mute', COMPLETED: 'mute',
  LATE: 'stop', FAILED: 'stop',
};
const LABELS = { PENDING_PAYMENT: 'Awaiting payment' };

export default function Badge({ value, tone }) {
  const t = tone || TONES[value] || 'mute';
  const text = LABELS[value] || (value.charAt(0) + value.slice(1).toLowerCase()).replace(/_/g, ' ');
  return <span className={`badge badge--${t}`}>{text}</span>;
}
