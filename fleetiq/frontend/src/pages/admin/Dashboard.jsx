import { Link } from 'react-router-dom';
import { Area, AreaChart, Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { analyticsApi, predictionApi } from '../../api/services';
import useAsync from '../../hooks/useAsync';
import PageHeader from '../../components/PageHeader';
import DemandMap from '../../components/DemandMap';
import { Loading, ErrorState } from '../../components/Status';
import { money, pct, typeLabel } from '../../utils/format';

export default function Dashboard() {
  const util = useAsync(() => analyticsApi.utilization(), []);
  const demand = useAsync(() => predictionApi.demand(), []);

  if (util.loading || demand.loading) return <Loading label="Loading fleet overview" />;
  if (util.error) return <ErrorState error={util.error} onRetry={util.reload} />;
  if (demand.error) return <ErrorState error={demand.error} onRetry={demand.reload} />;

  const u = util.data;
  const hottest = [...demand.data.zones].sort((a, b) => b.demand - a.demand)[0];

  return (
    <>
      <PageHeader title="Fleet overview" subtitle="Live position of the fleet and where demand is heading." />

      <section className="headline">
        <div className="headline__main">
          <p className="muted">Right now</p>
          <p className="headline__text">
            {hottest.name} is running at {pct(hottest.demand)} of predicted capacity with {hottest.searches} searches in the last hour.
            Prices there are at {hottest.multiplier.toFixed(2)}×.
          </p>
          <Link to="/admin/allocation" className="btn btn--primary btn--sm">Review suggested vehicle moves</Link>
        </div>
        <dl className="kpis">
          <div><dt>Fleet in use</dt><dd>{pct(u.utilizationRate)}</dd></div>
          <div><dt>On rent</dt><dd>{u.rented}<span className="muted"> / {u.fleetSize}</span></dd></div>
          <div><dt>In workshop</dt><dd>{u.inMaintenance}</dd></div>
          <div><dt>Revenue today</dt><dd>{money(u.revenueToday)}</dd></div>
        </dl>
      </section>

      <div className="grid-2">
        <section className="panel panel--wide">
          <h2>Demand by pickup area</h2>
          <DemandMap zones={demand.data.zones} />
        </section>

        <section className="panel">
          <h2>Utilisation, last 14 days</h2>
          <div className="chart">
            <ResponsiveContainer width="100%" height={240}>
              <AreaChart data={u.trend} margin={{ left: -18, right: 8, top: 8 }}>
                <CartesianGrid stroke="var(--line)" vertical={false} />
                <XAxis dataKey="date" tick={{ fontSize: 12 }} interval={2} />
                <YAxis unit="%" tick={{ fontSize: 12 }} domain={[0, 100]} />
                <Tooltip formatter={(v) => `${v}%`} />
                <Area dataKey="utilization" name="Utilisation" stroke="var(--ink)" fill="var(--amber)" fillOpacity={0.35} strokeWidth={2} />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        </section>

        <section className="panel">
          <h2>Utilisation by vehicle type</h2>
          <div className="chart">
            <ResponsiveContainer width="100%" height={240}>
              <BarChart data={u.byType.map((t) => ({ ...t, label: typeLabel[t.type] }))} layout="vertical" margin={{ left: 16, right: 16 }}>
                <CartesianGrid stroke="var(--line)" horizontal={false} />
                <XAxis type="number" unit="%" domain={[0, 100]} tick={{ fontSize: 12 }} />
                <YAxis type="category" dataKey="label" tick={{ fontSize: 12 }} width={84} />
                <Tooltip formatter={(v) => `${v}%`} />
                <Bar dataKey="utilization" name="Utilisation" fill="var(--ink)" radius={[0, 3, 3, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </section>
      </div>
    </>
  );
}
