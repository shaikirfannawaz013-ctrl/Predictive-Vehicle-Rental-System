import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import {
  Search, CalendarCheck, Gauge, TrendingUp, Wrench, ShieldAlert, Clock, Route as RouteIcon, LogOut,
} from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { USE_MOCKS } from '../api/client';
import NotificationBell from './NotificationBell';
import Toast from './Toast';

const customerNav = [
  { to: '/search', label: 'Find a vehicle', icon: Search },
  { to: '/bookings', label: 'My bookings', icon: CalendarCheck },
];
const adminNav = [
  { to: '/admin', label: 'Fleet overview', icon: Gauge, end: true },
  { to: '/admin/demand', label: 'Demand & pricing', icon: TrendingUp },
  { to: '/admin/allocation', label: 'Fleet allocation', icon: RouteIcon },
  { to: '/admin/maintenance', label: 'Maintenance', icon: Wrench },
  { to: '/admin/late-returns', label: 'Late returns', icon: Clock },
  { to: '/admin/risk', label: 'Customer risk', icon: ShieldAlert },
];

export default function Layout() {
  const { user, isAdmin, logout } = useAuth();
  const navigate = useNavigate();
  const nav = isAdmin ? adminNav : customerNav;

  return (
    <div className="shell">
      <a className="skip-link" href="#main">Skip to content</a>
      <aside className="sidebar">
        <div className="brand">
          <span className="brand__mark" aria-hidden="true"><span>IQ</span></span>
          <span className="brand__name">FleetIQ</span>
        </div>
        <nav aria-label="Main">
          <ul className="nav">
            {nav.map(({ to, label, icon: Icon, end }) => (
              <li key={to}>
                <NavLink to={to} end={end} className={({ isActive }) => `nav__link ${isActive ? 'is-active' : ''}`}>
                  <Icon size={18} aria-hidden="true" />
                  <span>{label}</span>
                </NavLink>
              </li>
            ))}
          </ul>
        </nav>
        <div className="sidebar__foot">
          {USE_MOCKS && <p className="sidebar__note">Demo data — no backend connected</p>}
          <div className="sidebar__user">
            <span>{user.name}</span>
            <span className="small">{isAdmin ? 'Fleet manager' : 'Customer'}</span>
          </div>
          <button
            className="nav__link nav__link--button"
            onClick={() => {
              logout();
              navigate('/login');
            }}
          >
            <LogOut size={18} aria-hidden="true" />
            <span>Sign out</span>
          </button>
        </div>
      </aside>

      <div className="main-col">
        <div className="topbar">
          <span className="topbar__region">Tirupati region</span>
          <NotificationBell />
        </div>
        <main id="main" className="content">
          <Outlet />
        </main>
      </div>
      <Toast />
    </div>
  );
}
