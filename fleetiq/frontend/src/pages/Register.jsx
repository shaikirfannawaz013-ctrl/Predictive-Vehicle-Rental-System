import { useState } from 'react';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { AuthFrame } from './Login';

export default function Register() {
  const { register, user } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState({ name: '', email: '', phone: '', licenseNumber: '', password: '' });
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  if (user) return <Navigate to="/" replace />;
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });

  const submit = async (e) => {
    e.preventDefault();
    if (form.password.length < 8) return setError('Password must be at least 8 characters.');
    setBusy(true);
    setError('');
    try {
      await register(form);
      navigate('/search', { replace: true });
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <AuthFrame title="Create your account">
      <form onSubmit={submit} className="stack">
        <label className="field"><span>Full name</span><input required value={form.name} onChange={set('name')} autoComplete="name" /></label>
        <label className="field"><span>Email</span><input type="email" required value={form.email} onChange={set('email')} autoComplete="email" /></label>
        <div className="row-2">
          <label className="field"><span>Phone</span><input type="tel" required pattern="[0-9]{10}" title="10-digit mobile number" value={form.phone} onChange={set('phone')} autoComplete="tel" /></label>
          <label className="field"><span>Driving licence no.</span><input required value={form.licenseNumber} onChange={set('licenseNumber')} /></label>
        </div>
        <label className="field"><span>Password</span><input type="password" required value={form.password} onChange={set('password')} autoComplete="new-password" /></label>
        {error && <p className="form-error" role="alert">{error}</p>}
        <button className="btn btn--primary" disabled={busy}>{busy ? 'Creating account…' : 'Create account'}</button>
        <p className="muted small">Already registered? <Link to="/login">Sign in</Link></p>
      </form>
    </AuthFrame>
  );
}
