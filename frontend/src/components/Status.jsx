import { AlertTriangle, Inbox } from 'lucide-react';

export function Loading({ label = 'Loading' }) {
  return (
    <div className="status" role="status" aria-live="polite">
      <span className="spinner" aria-hidden="true" />
      <span>{label}…</span>
    </div>
  );
}

export function ErrorState({ error, onRetry }) {
  return (
    <div className="status status--error" role="alert">
      <AlertTriangle size={20} aria-hidden="true" />
      <span>{error?.message || 'Something went wrong.'}</span>
      {onRetry && <button className="btn btn--ghost" onClick={onRetry}>Try again</button>}
    </div>
  );
}

export function Empty({ title, children }) {
  return (
    <div className="status status--empty">
      <Inbox size={22} aria-hidden="true" />
      <strong>{title}</strong>
      {children && <span>{children}</span>}
    </div>
  );
}
