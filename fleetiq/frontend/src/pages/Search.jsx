import { useEffect, useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { Users, Fuel, Cog } from 'lucide-react';
import { vehicleApi, predictionApi } from '../api/services';
import PageHeader from '../components/PageHeader';
import SurgePlate from '../components/SurgePlate';
import { Loading, ErrorState, Empty } from '../components/Status';
import { money, pct, toLocalInput, VEHICLE_TYPES, typeLabel } from '../utils/format';

function defaultDates() {
  const start = new Date();
  start.setDate(start.getDate() + 1);
  start.setHours(9, 0, 0, 0);
  const end = new Date(start);
  end.setDate(end.getDate() + 2);
  return { from: toLocalInput(start), to: toLocalInput(end) };
}

export default function Search() {
  const [params, setParams] = useSearchParams();
  const init = defaultDates();
  const [form, setForm] = useState({
    zoneId: params.get('zoneId') || '',
    type: params.get('type') || '',
    from: params.get('from') || init.from,
    to: params.get('to') || init.to,
  });
  const [zones, setZones] = useState([]);
  const [results, setResults] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  useEffect(() => {
    predictionApi.demand().then((d) => setZones(d.zones)).catch(() => {});
  }, []);

  const runSearch = async (f = form) => {
    setLoading(true);
    setError(null);
    try {
      // Each search is also published to Kafka by the backend and feeds the demand model.
      const data = await vehicleApi.search({
        zoneId: f.zoneId || undefined,
        type: f.type || undefined,
        startTime: new Date(f.from).toISOString(),
        endTime: new Date(f.to).toISOString(),
      });
      setResults(data);
    } catch (e) {
      setError(e);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    runSearch();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const submit = (e) => {
    e.preventDefault();
    if (new Date(form.to) <= new Date(form.from)) return setError(new Error('Return time must be after pickup time.'));
    setParams(Object.fromEntries(Object.entries(form).filter(([, v]) => v)));
    runSearch();
  };

  const zone = useMemo(() => zones.find((z) => z.id === form.zoneId), [zones, form.zoneId]);
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });
  const detailQuery = `?from=${encodeURIComponent(form.from)}&to=${encodeURIComponent(form.to)}`;

  return (
    <>
      <PageHeader title="Find a vehicle" subtitle="Prices shown are per day and update with local demand." />

      <form className="search-bar" onSubmit={submit}>
        <label className="field">
          <span>Pickup area</span>
          <select value={form.zoneId} onChange={set('zoneId')}>
            <option value="">Anywhere near Tirupati</option>
            {zones.map((z) => <option key={z.id} value={z.id}>{z.name}</option>)}
          </select>
        </label>
        <label className="field">
          <span>Vehicle type</span>
          <select value={form.type} onChange={set('type')}>
            <option value="">Any type</option>
            {VEHICLE_TYPES.map((t) => <option key={t} value={t}>{typeLabel[t]}</option>)}
          </select>
        </label>
        <label className="field">
          <span>Pickup</span>
          <input type="datetime-local" value={form.from} onChange={set('from')} required />
        </label>
        <label className="field">
          <span>Return</span>
          <input type="datetime-local" value={form.to} onChange={set('to')} required />
        </label>
        <button className="btn btn--primary">Search</button>
      </form>

      {zone && zone.multiplier > 1.05 && (
        <div className="notice notice--amber">
          <SurgePlate multiplier={zone.multiplier} size="sm" />
          <p>
            {zone.searches} people searched near {zone.name} in the last hour, so prices here are higher than usual.
            Nearby areas with lower demand may cost less.
          </p>
        </div>
      )}

      {loading && <Loading label="Searching vehicles" />}
      {error && <ErrorState error={error} onRetry={() => runSearch()} />}
      {!loading && !error && results?.length === 0 && (
        <Empty title="No vehicles match these filters">Try another area, a different type, or other dates.</Empty>
      )}

      {!loading && !error && results?.length > 0 && (
        <ul className="vehicle-list">
          {results.map((v) => (
            <li key={v.id} className="vehicle-row">
              <div className="vehicle-row__main">
                <span className="vehicle-row__type">{typeLabel[v.type]}</span>
                <h2>{v.name}</h2>
                <p className="vehicle-row__specs">
                  <span><Users size={15} aria-hidden="true" /> {v.seats} seats</span>
                  <span><Fuel size={15} aria-hidden="true" /> {v.fuel}</span>
                  <span><Cog size={15} aria-hidden="true" /> {v.transmission}</span>
                </p>
                <p className="small muted">Pickup at {v.zoneName}</p>
              </div>
              <div className="vehicle-row__avail">
                <Meter value={v.availabilityProbability} />
                <span className="small">
                  {pct(v.availabilityProbability)} chance it's still free if you book later today
                </span>
              </div>
              <div className="vehicle-row__price">
                <SurgePlate multiplier={v.demandMultiplier} size="sm" />
                <span className="price">{money(v.currentRate)}<span className="small muted">/day</span></span>
                {v.currentRate !== v.baseRate && <span className="small muted strike">{money(v.baseRate)}</span>}
                <Link className="btn btn--primary btn--sm" to={`/vehicles/${v.id}${detailQuery}`}>View and book</Link>
              </div>
            </li>
          ))}
        </ul>
      )}
    </>
  );
}

function Meter({ value }) {
  const tone = value < 0.35 ? 'stop' : value < 0.6 ? 'amber' : 'go';
  return (
    <span className="meter" role="meter" aria-valuemin={0} aria-valuemax={100} aria-valuenow={Math.round(value * 100)} aria-label="Predicted availability">
      <span className={`meter__fill meter__fill--${tone}`} style={{ width: `${value * 100}%` }} />
    </span>
  );
}
