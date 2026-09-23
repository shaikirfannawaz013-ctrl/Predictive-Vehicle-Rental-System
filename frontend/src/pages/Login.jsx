import { useState } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { USE_MOCKS } from '../api/client';

export default function Login() {
  const { login, user } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [form, setForm] = useState({ email: '', password: '' });
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  if (user) return <Navigate to="/" replace />;

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true);
    setError('');
    try {
      const u = await login(form);
      navigate(location.state?.from || (u.role === 'ADMIN' ? '/admin' : '/search'), { replace: true });
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <AuthFrame title="Sign in">
      <form onSubmit={submit} className="stack">
        <label className="field">
          <span>Email</span>
          <input type="email" required autoComplete="email" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} />
        </label>
        <label className="field">
          <span>Password</span>
          <input type="password" required autoComplete="current-password" value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} />
        </label>
        {error && <p className="form-error" role="alert">{error}</p>}
        <button className="btn btn--primary" disabled={busy}>{busy ? 'Signing in…' : 'Sign in'}</button>
        <p className="muted small">New here? <Link to="/register">Create an account</Link></p>
        {USE_MOCKS && (
          <p className="hint">Demo mode: any password works. Use an email containing “admin” to open the fleet manager view.</p>
        )}
      </form>
    </AuthFrame>
  );
}

export function AuthFrame({ title, children }) {
  return (
    <div className="auth">
      <section className="auth__side" aria-hidden="true">
        <div className="brand brand--lg">
          <span className="brand__mark"><span>IQ</span></span>
          <span className="brand__name">FleetIQ</span>
        </div>
        <p className="auth__pitch">Rent a car or bike around Tirupati. Prices follow live demand, so booking early on busy weekends saves you money.</p>
        <div className="auth__signs">
          <span className="plate plate--high plate--md"><span className="plate__value">1.57×</span><span className="plate__label">Tirupati Central</span></span>
          <span className="plate plate--rising plate--md"><span className="plate__value">1.30×</span><span className="plate__label">Renigunta Airport</span></span>
          <span className="plate plate--normal plate--md"><span className="plate__value">1.00×</span><span className="plate__label">Puttur</span></span>
        </div>
      </section>
      <section className="auth__form">
        <h1>{title}</h1>
        {children}
      </section>
    </div>
  );
}
