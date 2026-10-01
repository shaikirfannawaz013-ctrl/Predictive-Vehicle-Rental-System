import { useEffect, useState } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';
import { vehicleApi, pricingApi, bookingApi } from '../api/services';
import useAsync from '../hooks/useAsync';
import SurgePlate from '../components/SurgePlate';
import { Loading, ErrorState } from '../components/Status';
import { money, toLocalInput, typeLabel } from '../utils/format';

export default function VehicleDetail() {
  const { id } = useParams();
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const { data: v, loading, error, reload } = useAsync(() => vehicleApi.get(id), [id]);

  const fallbackStart = new Date(Date.now() + 864e5);
  const [from, setFrom] = useState(params.get('from') || toLocalInput(fallbackStart));
  const [to, setTo] = useState(params.get('to') || toLocalInput(new Date(fallbackStart.getTime() + 2 * 864e5)));
  const [quote, setQuote] = useState(null);
  const [quoteError, setQuoteError] = useState('');
  const [booking, setBooking] = useState(false);

  useEffect(() => {
    if (new Date(to) <= new Date(from)) {
      setQuote(null);
      return setQuoteError('Return time must be after pickup time.');
    }
    setQuoteError('');
    const range = { vehicleId: id, startTime: new Date(from).toISOString(), endTime: new Date(to).toISOString() };
    const t = setTimeout(() => {
      pricingApi.quote(range).then(setQuote).catch((e) => setQuoteError(e.message));
    }, 250);
    return () => clearTimeout(t);
  }, [id, from, to]);

  const reserve = async () => {
    setBooking(true);
    try {
      const b = await bookingApi.create({ vehicleId: Number(id), startTime: new Date(from).toISOString(), endTime: new Date(to).toISOString() });
      navigate(`/checkout/${b.id}`, { state: { booking: b, quote, vehicle: v } });
    } catch (e) {
      setQuoteError(e.message);
      setBooking(false);
    }
  };

  if (loading) return <Loading label="Loading vehicle" />;
  if (error) return <ErrorState error={error} onRetry={reload} />;

  return (
    <>
      <button type="button" onClick={() => navigate(-1)} className="back-link">
        <ArrowLeft size={16} aria-hidden="true" /> Back to results
      </button>
      <div className="detail">
        <section className="detail__info">
          <span className="vehicle-row__type">{typeLabel[v.type]}</span>
          <h1>{v.name}</h1>
          <dl className="spec-grid">
            <div><dt>Seats</dt><dd>{v.seats}</dd></div>
            <div><dt>Fuel</dt><dd>{v.fuel}</dd></div>
            <div><dt>Gearbox</dt><dd>{v.transmission}</dd></div>
            <div><dt>Odometer</dt><dd>{v.odometer.toLocaleString('en-IN')} km</dd></div>
            <div><dt>Pickup</dt><dd>{v.zoneName}</dd></div>
            <div><dt>Base rate</dt><dd>{money(v.baseRate)}/day</dd></div>
          </dl>
          <p className="muted">
            Fuel is returned at the level you received it. Late returns are charged hourly after a 30-minute grace period.
          </p>
        </section>

        <aside className="quote" aria-labelledby="quote-title">
          <h2 id="quote-title">Your trip</h2>
          <label className="field"><span>Pickup</span><input type="datetime-local" value={from} onChange={(e) => setFrom(e.target.value)} /></label>
          <label className="field"><span>Return</span><input type="datetime-local" value={to} onChange={(e) => setTo(e.target.value)} /></label>

          {quoteError && <p className="form-error" role="alert">{quoteError}</p>}
          {quote && (
            <>
              <div className="quote__surge">
                <SurgePlate multiplier={quote.multiplier} />
                {quote.reasons.length > 0 && (
                  <ul className="quote__reasons">
                    {quote.reasons.map((r) => <li key={r}>{r}</li>)}
                  </ul>
                )}
              </div>
              <dl className="quote__lines">
                <div><dt>{money(quote.baseRate)} × {quote.multiplier.toFixed(2)} × {quote.days} day{quote.days > 1 ? 's' : ''}</dt><dd>{money(quote.subtotal)}</dd></div>
                <div><dt>GST (18%)</dt><dd>{money(quote.tax)}</dd></div>
                <div className="quote__total"><dt>Total</dt><dd>{money(quote.total)}</dd></div>
                <div className="small muted"><dt>Refundable deposit at pickup</dt><dd>{money(quote.deposit)}</dd></div>
              </dl>
              <p className="small muted">This price is held for 10 minutes once you reserve.</p>
            </>
          )}
          <button className="btn btn--primary btn--block" onClick={reserve} disabled={!quote || booking}>
            {booking ? 'Reserving…' : 'Reserve and pay'}
          </button>
        </aside>
      </div>
    </>
  );
}
