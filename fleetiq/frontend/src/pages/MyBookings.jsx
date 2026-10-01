import { Link, useNavigate } from 'react-router-dom';
import { bookingApi } from '../api/services';
import useAsync from '../hooks/useAsync';
import PageHeader from '../components/PageHeader';
import Badge from '../components/Badge';
import { Loading, ErrorState, Empty } from '../components/Status';
import { money, dateTime } from '../utils/format';

export default function MyBookings() {
  const { data, loading, error, reload, setData } = useAsync(() => bookingApi.mine(), []);
  const navigate = useNavigate();

  const cancel = async (id) => {
    if (!window.confirm(`Cancel booking #${id}? Paid bookings are refunded within 5–7 working days.`)) return;
    const updated = await bookingApi.cancel(id);
    setData(data.map((b) => (b.id === id ? updated : b)));
  };

  return (
    <>
      <PageHeader title="My bookings" />
      {loading && <Loading label="Loading bookings" />}
      {error && <ErrorState error={error} onRetry={reload} />}
      {data?.length === 0 && (
        <Empty title="No bookings yet"><Link to="/search">Find a vehicle</Link> to make your first booking.</Empty>
      )}
      {data?.length > 0 && (
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr><th>Booking</th><th>Vehicle</th><th>Pickup</th><th>Return</th><th className="num">Total</th><th>Status</th><th><span className="sr-only">Actions</span></th></tr>
            </thead>
            <tbody>
              {data.map((b) => (
                <tr key={b.id}>
                  <td>#{b.id}</td>
                  <td>{b.vehicleName}</td>
                  <td>{dateTime(b.startTime)}</td>
                  <td>{dateTime(b.endTime)}</td>
                  <td className="num">{money(b.total)}</td>
                  <td><Badge value={b.status} /></td>
                  <td className="actions">
                    {b.status === 'PENDING_PAYMENT' && (
                      <button className="btn btn--sm btn--primary" onClick={() => navigate(`/checkout/${b.id}`, { state: { booking: b } })}>Pay now</button>
                    )}
                    {['PENDING_PAYMENT', 'CONFIRMED'].includes(b.status) && (
                      <button className="btn btn--sm btn--ghost" onClick={() => cancel(b.id)}>Cancel</button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
}
