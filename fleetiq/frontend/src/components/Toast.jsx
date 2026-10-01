import { X } from 'lucide-react';
import { useNotifications } from '../context/NotificationContext';

export default function Toast() {
  const { toast, dismissToast } = useNotifications();
  if (!toast) return null;
  return (
    <div className="toast" role="status" aria-live="polite">
      <span>{toast.message}</span>
      <button onClick={dismissToast} aria-label="Dismiss"><X size={16} /></button>
    </div>
  );
}
