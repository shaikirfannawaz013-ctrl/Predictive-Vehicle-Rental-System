import { useState } from 'react';
import { predictionApi } from '../../api/services';
import useAsync from '../../hooks/useAsync';
import PageHeader from '../../components/PageHeader';
import Badge from '../../components/Badge';
import { Loading, ErrorState } from '../../components/Status';

export default function Maintenance() {
  const { data, loading, error, reload } = useAsync(() => predictionApi.maintenance(), []);
  const [scheduled, setScheduled] = useState({});

  const schedule = async (id) => {
    await predictionApi.scheduleMaintenance(id);
    setScheduled((s) => ({ ...s, [id]: true }));
  };

  return (
    <>
      <PageHeader title="Predictive maintenance" subtitle="Vehicles ordered by health score. Lower scores need attention sooner." />
      {loading && <Loading label="Loading health predictions" />}
      {error && <ErrorState error={error} onRetry={reload} />}
      {data && (
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr><th>Vehicle</th><th>Health</th><th>Likely issue</th><th className="num">Failure risk</th><th className="num">Service within</th><th className="num">Odometer</th><th><span className="sr-only">Actions</span></th></tr>
            </thead>
            <tbody>
              {data.map((m) => {
                const tone = m.healthScore < 50 ? 'stop' : m.healthScore < 70 ? 'amber' : 'go';
                const isScheduled = scheduled[m.vehicleId] || m.status === 'MAINTENANCE';
                return (
                  <tr key={m.vehicleId}>
                    <td>{m.vehicleName}</td>
                    <td>
                      <span className="health">
                        <span className="meter meter--sm"><span className={`meter__fill meter__fill--${tone}`} style={{ width: `${m.healthScore}%` }} /></span>
                        {m.healthScore}
                      </span>
                    </td>
                    <td>{m.component}</td>
                    <td className="num">{Math.round(m.failureProbability * 100)}%</td>
                    <td className="num">{m.dueInDays} days</td>
                    <td className="num">{m.odometer.toLocaleString('en-IN')} km</td>
                    <td className="actions">
                      {isScheduled ? <Badge value="SCHEDULED" /> : (
                        <button className={`btn btn--sm ${m.healthScore < 50 ? 'btn--primary' : 'btn--ghost'}`} onClick={() => schedule(m.vehicleId)}>
                          Schedule service
                        </button>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
}
