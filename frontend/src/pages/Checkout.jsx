import { useState } from 'react';
import { Link, useLocation, useParams } from 'react-router-dom';
import { CheckCircle2 } from 'lucide-react';
import { paymentApi } from '../api/services';
import PageHeader from '../components/PageHeader';
import { money, dateTime } from '../utils/format';

// Payment step. With a real gateway (e.g. Razorpay), POST /payments returns an order id;
// open the gateway checkout with it, then call /payments/{id}/confirm with the gateway signature.
export default function Checkout() {
  const { bookingId } = useParams();
  const { state } = useLocation();
  const booking = state?.booking;
  const [method, setMethod] = useState('UPI');
  const [upi, setUpi] = useState('');
  const [status, setStatus] = useState('idle');
  const [error, setError] = useState('');

  const pay = async (e) => {
    e.preventDefault();
    setStatus('paying');
    setError('');
    try {
      const p = await paymentApi.create({ bookingId: Number(bookingId), method, upiId: method === 'UPI' ? upi : undefined });
      const r = await paymentApi.confirm(p.paymentId);
      if (r.status !== 'SUCCESS') throw new Error('The payment was declined. No money was taken. Try another method.');
      setStatus('done');
    } catch (err) {
      setError(err.message);
      setStatus('idle');
    }
  };

  if (status === 'done') {
    return (
      <div className="success">
        <CheckCircle2 size={40} aria-hidden="true" />
        <h1>Booking confirmed</h1>
        <p>Booking #{bookingId} is paid. We've sent the pickup details to your email and phone.</p>
        <Link className="btn btn--primary" to="/bookings">Go to my bookings</Link>
      </div>
    );
  }

  return (
    <>
      <PageHeader title="Pay for your booking" subtitle={`Booking #${bookingId}`} />
      <div className="detail">
        <form className="detail__info stack" onSubmit={pay}>
          <fieldset className="segmented">
            <legend>Payment method</legend>
            {['UPI', 'CARD', 'NETBANKING'].map((m) => (
              <label key={m} className={method === m ? 'is-active' : ''}>
                <input type="radio" name="method" value={m} checked={method === m} onChange={() => setMethod(m)} />
                {m === 'UPI' ? 'UPI' : m === 'CARD' ? 'Card' : 'Net banking'}
              </label>
            ))}
          </fieldset>
          {method === 'UPI' && (
            <label className="field">
              <span>UPI ID</span>
              <input required placeholder="name@bank" pattern="[\w.\-]+@[\w]+" value={upi} onChange={(e) => setUpi(e.target.value)} />
            </label>
          )}
          {method !== 'UPI' && <p className="muted">You'll be sent to the secure payment page to finish.</p>}
          {error && <p className="form-error" role="alert">{error}</p>}
          <button className="btn btn--primary" disabled={status === 'paying'}>
            {status === 'paying' ? 'Processing payment…' : `Pay ${booking ? money(booking.total) : ''}`}
          </button>
        </form>
        {booking && (
          <aside className="quote">
            <h2>{booking.vehicleName}</h2>
            <dl className="quote__lines">
              <div><dt>Pickup</dt><dd>{dateTime(booking.startTime)}</dd></div>
              <div><dt>Return</dt><dd>{dateTime(booking.endTime)}</dd></div>
              <div className="quote__total"><dt>Total</dt><dd>{money(booking.total)}</dd></div>
            </dl>
          </aside>
        )}
      </div>
    </>
  );
}
