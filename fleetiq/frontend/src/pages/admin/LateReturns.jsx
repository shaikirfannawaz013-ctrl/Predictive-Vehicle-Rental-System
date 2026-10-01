import { useEffect } from 'react';
import { bookingApi } from '../../api/services';
import useAsync from '../../hooks/useAsync';
import PageHeader from '../../components/PageHeader';
import { Loading, ErrorState, Empty } from '../../components/Status';
import { money, dateTime } from '../../utils/format';

// The backend runs a scheduled job that flags overdue bookings and publishes LATE_RETURN events.
// This page polls every minute so the list stays current.
export default function LateReturns() {
  const { data, loading, error, reload } = useAsync(() => bookingApi.late(), []);

  useEffect(() => {
    const t = setInterval(reload, 60000);
    return () => clearInterval(t);
  }, [reload]);

  return (
    <>
      <PageHeader title="Late returns" subtitle="Bookings past their return time. Penalty accrues at ₹250 per hour." actions={<button className="btn btn--ghost" onClick={reload}>Refresh</button>} />
      {loading && !data && <Loading label="Checking returns" />}
      {error && <ErrorState error={error} onRetry={reload} />}
      {data?.length === 0 && <Empty title="Every vehicle is back on time">Nothing is overdue right now.</Empty>}
      {data?.length > 0 && (
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr><th>Booking</th><th>Customer</th><th>Vehicle</th><th>Due back</th><th className="num">Overdue</th><th className="num">Penalty so far</th></tr>
            </thead>
            <tbody>
              {data.map((b) => (
                <tr key={b.id} className={b.overdueHours >= 4 ? 'row--alert' : ''}>
                  <td>#{b.id}</td>
                  <td>{b.customer}</td>
                  <td>{b.vehicleName}</td>
                  <td>{dateTime(b.endTime)}</td>
                  <td className="num">{b.overdueHours} h</td>
                  <td className="num">{money(b.penalty)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
}
