import { riskApi } from '../../api/services';
import useAsync from '../../hooks/useAsync';
import PageHeader from '../../components/PageHeader';
import { Loading, ErrorState } from '../../components/Status';

const band = (s) => (s >= 70 ? { label: 'High', tone: 'stop', action: 'Require double deposit' } : s >= 40 ? { label: 'Medium', tone: 'warn', action: 'Verify licence at pickup' } : { label: 'Low', tone: 'go', action: 'No action' });

export default function CustomerRisk() {
  const { data, loading, error, reload } = useAsync(() => riskApi.customers(), []);
  return (
    <>
      <PageHeader title="Customer risk" subtitle="Scores from 0 to 100 combine late returns, damage claims and failed payments." />
      {loading && <Loading label="Scoring customers" />}
      {error && <ErrorState error={error} onRetry={reload} />}
      {data && (
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr><th>Customer</th><th className="num">Score</th><th>Risk</th><th className="num">Bookings</th><th className="num">Late returns</th><th className="num">Damage claims</th><th className="num">Failed payments</th><th>Recommended action</th></tr>
            </thead>
            <tbody>
              {data.slice().sort((a, b) => b.score - a.score).map((c) => {
                const b = band(c.score);
                return (
                  <tr key={c.customerId}>
                    <td>{c.name}</td>
                    <td className="num"><strong>{c.score}</strong></td>
                    <td><span className={`badge badge--${b.tone}`}>{b.label}</span></td>
                    <td className="num">{c.totalBookings}</td>
                    <td className="num">{c.lateReturns}</td>
                    <td className="num">{c.damageClaims}</td>
                    <td className="num">{c.paymentFailures}</td>
                    <td>{b.action}</td>
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
