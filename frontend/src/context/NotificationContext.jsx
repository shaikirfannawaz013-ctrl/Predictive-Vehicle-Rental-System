import { createContext, useContext, useEffect, useState } from 'react';
import { notificationApi } from '../api/services';
import { USE_MOCKS, tokenStore } from '../api/client';
import { mockStream } from '../api/mock';
import { useAuth } from './AuthContext';

const NotificationContext = createContext(null);

// Loads existing notifications, then listens for live ones.
// Backend: Kafka consumer → SseEmitter at GET /api/notifications/stream?token=<jwt>
export function NotificationProvider({ children }) {
  const { user } = useAuth();
  const [items, setItems] = useState([]);
  const [toast, setToast] = useState(null);

  useEffect(() => {
    if (!user) return setItems([]);
    // Real backend filters by user; the mock hides fleet-only alerts from customers.
    const visible = (n) => user.role === 'ADMIN' || !USE_MOCKS || !['LATE_RETURN', 'MAINTENANCE'].includes(n.type);
    notificationApi.list().then((list) => setItems(list.filter(visible))).catch(() => {});

    const push = (n) => {
      if (!visible(n)) return;
      setItems((prev) => [n, ...prev].slice(0, 50));
      setToast(n);
    };

    if (USE_MOCKS) return mockStream(push);

    const base = import.meta.env.VITE_API_BASE_URL || '/api';
    const es = new EventSource(`${base}/notifications/stream?token=${encodeURIComponent(tokenStore.get())}`);
    es.addEventListener('notification', (e) => push(JSON.parse(e.data)));
    es.onerror = () => {}; // EventSource reconnects on its own
    return () => es.close();
  }, [user]);

  useEffect(() => {
    if (!toast) return;
    const t = setTimeout(() => setToast(null), 6000);
    return () => clearTimeout(t);
  }, [toast]);

  const markRead = (id) => {
    setItems((prev) => prev.map((n) => (n.id === id ? { ...n, read: true } : n)));
    notificationApi.markRead(id).catch(() => {});
  };
  const markAllRead = () => items.filter((n) => !n.read).forEach((n) => markRead(n.id));

  return (
    <NotificationContext.Provider
      value={{ items, unread: items.filter((n) => !n.read).length, markRead, markAllRead, toast, dismissToast: () => setToast(null) }}
    >
      {children}
    </NotificationContext.Provider>
  );
}

export const useNotifications = () => useContext(NotificationContext);
