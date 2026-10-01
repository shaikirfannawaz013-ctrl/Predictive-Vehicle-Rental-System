import { useEffect, useRef, useState } from 'react';
import { Bell } from 'lucide-react';
import { useNotifications } from '../context/NotificationContext';
import { timeAgo } from '../utils/format';

const TYPE_LABEL = { DEMAND_SURGE: 'Demand', LATE_RETURN: 'Late return', MAINTENANCE: 'Maintenance', BOOKING: 'Booking', PAYMENT: 'Payment' };

export default function NotificationBell() {
  const { items, unread, markRead, markAllRead } = useNotifications();
  const [open, setOpen] = useState(false);
  const ref = useRef(null);

  useEffect(() => {
    const close = (e) => ref.current && !ref.current.contains(e.target) && setOpen(false);
    const esc = (e) => e.key === 'Escape' && setOpen(false);
    document.addEventListener('mousedown', close);
    document.addEventListener('keydown', esc);
    return () => {
      document.removeEventListener('mousedown', close);
      document.removeEventListener('keydown', esc);
    };
  }, []);

  return (
    <div className="bell" ref={ref}>
      <button
        className="bell__btn"
        aria-label={`Notifications, ${unread} unread`}
        aria-expanded={open}
        onClick={() => setOpen((o) => !o)}
      >
        <Bell size={20} aria-hidden="true" />
        {unread > 0 && <span className="bell__count">{unread}</span>}
      </button>
      {open && (
        <div className="bell__panel" role="dialog" aria-label="Notifications">
          <div className="bell__head">
            <strong>Notifications</strong>
            {unread > 0 && <button className="linkish" onClick={markAllRead}>Mark all as read</button>}
          </div>
          {items.length === 0 ? (
            <p className="muted bell__empty">You're all caught up.</p>
          ) : (
            <ul>
              {items.map((n) => (
                <li key={n.id} className={n.read ? '' : 'is-unread'}>
                  <button onClick={() => markRead(n.id)}>
                    <span className={`bell__type bell__type--${n.type}`}>{TYPE_LABEL[n.type] || n.type}</span>
                    <span>{n.message}</span>
                    <span className="small muted">{timeAgo(n.createdAt)}</span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  );
}
