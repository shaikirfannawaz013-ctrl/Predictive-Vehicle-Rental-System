import { useState } from 'react';
import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { predictionApi } from '../../api/services';
import useAsync from '../../hooks/useAsync';
import PageHeader from '../../components/PageHeader';
import DemandMap from '../../components/DemandMap';
import SurgePlate from '../../components/SurgePlate';
import { Loading, ErrorState } from '../../components/Status';
import { dateTime, typeLabel } from '../../utils/format';

const COLORS = { SUV: 'var(--ink)', SEDAN: 'var(--amber)', HATCHBACK: 'var(--go)', EV: 'var(--info)', BIKE: 'var(--stop)' };

export default function DemandPricing() {
  const { data, loading, error, reload } = useAsync(() => predictionApi.demand({ days: 7 }), []);
  const [selected, setSelected] = useState(null);

  if (loading) return <Loading label="Loading demand forecast" />;
  if (error) return <ErrorState error={error} onRetry={reload} />;

  const zone = data.zones.find((z) => z.id === selected) || [...data.zones].sort((a, b) => b.demand - a.demand)[0];

  return (
    <>
      <PageHeader
        title="Demand and pricing"
        subtitle={`Forecast from model ${data.modelVersion}, updated ${dateTime(data.generatedAt)}.`}
        actions={<button className="btn btn--ghost" onClick={reload}>Refresh forecast</button>}
      />

      <div className="grid-2">
        <section className="panel panel--wide">
          <h2>Select an area to see why its price changed</h2>
          <DemandMap zones={data.zones} selected={zone.id} onSelect={setSelected} />
        </section>

        <section className="panel">
          <h2>{zone.name}</h2>
          <div className="zone-detail">
            <SurgePlate multiplier={zone.multiplier} size="lg" />
            <dl className="spec-grid spec-grid--2">
              <div><dt>Predicted demand</dt><dd>{Math.round(zone.demand * 100)}%</dd></div>
              <div><dt>Searches, last hour</dt><dd>{zone.searches}</dd></div>
              <div><dt>Vehicles parked here</dt><dd>{zone.supply}</dd></div>
              <div><dt>Searches per vehicle</dt><dd>{(zone.searches / zone.supply).toFixed(1)}</dd></div>
            </dl>
            <p className="muted small">
              Multiplier = 1 + 1.1 × (demand − 0.40), capped at 2.0×. Weekend pickups add 0.10×. Rules live in the pricing
              service and are recalculated each time a search event arrives on Kafka.
            </p>
          </div>
        </section>

        <section className="panel">
          <h2>Bookings expected over the next 7 days</h2>
          <div className="chart">
            <ResponsiveContainer width="100%" height={260}>
              <BarChart data={data.forecast} margin={{ left: -18, right: 8, top: 8 }}>
                <CartesianGrid stroke="var(--line)" vertical={false} />
                <XAxis dataKey="day" tick={{ fontSize: 12 }} />
                <YAxis tick={{ fontSize: 12 }} />
                <Tooltip />
                <Legend formatter={(v) => typeLabel[v]} wrapperStyle={{ fontSize: 12 }} />
                {Object.keys(COLORS).map((k) => <Bar key={k} dataKey={k} stackId="a" fill={COLORS[k]} />)}
              </BarChart>
            </ResponsiveContainer>
          </div>
        </section>
      </div>
    </>
  );
}
