import { useState } from 'react';
import { ArrowRight } from 'lucide-react';
import { allocationApi } from '../../api/services';
import useAsync from '../../hooks/useAsync';
import PageHeader from '../../components/PageHeader';
import Badge from '../../components/Badge';
import { Loading, ErrorState, Empty } from '../../components/Status';
import { money, typeLabel } from '../../utils/format';

export default function Allocation() {
  const { data, loading, error, reload } = useAsync(() => allocationApi.recommendations(), []);
  const [done, setDone] = useState({});
  const [busy, setBusy] = useState(null);

  const apply = async (id) => {
    setBusy(id);
    try {
      const r = await allocationApi.apply(id);
      setDone((d) => ({ ...d, [id]: r.status }));
    } finally {
      setBusy(null);
    }
  };

  return (
    <>
      <PageHeader title="Fleet allocation" subtitle="Moves the model suggests to put vehicles where bookings are expected." />
      {loading && <Loading label="Calculating suggested moves" />}
      {error && <ErrorState error={error} onRetry={reload} />}
      {data?.length === 0 && <Empty title="No moves needed">Supply matches predicted demand in every area.</Empty>}
      {data?.length > 0 && (
        <ol className="moves">
          {data
            .slice()
            .sort((a, b) => b.expectedGain - a.expectedGain)
            .map((m) => (
              <li key={m.id} className="move">
                <div className="move__route">
                  <span>{m.fromZone}</span>
                  <ArrowRight size={18} aria-label="to" />
                  <strong>{m.toZone}</strong>
                </div>
                <div className="move__body">
                  <p><strong>{m.vehicleName}</strong> <span className="muted">({typeLabel[m.type]})</span></p>
                  <p className="muted small">{m.reason}</p>
                </div>
                <div className="move__gain">
                  <span className="price">+{money(m.expectedGain)}</span>
                  <span className="small muted">expected extra revenue</span>
                </div>
                <div className="move__action">
                  {done[m.id] ? (
                    <Badge value={done[m.id]} />
                  ) : (
                    <button className="btn btn--primary btn--sm" disabled={busy === m.id} onClick={() => apply(m.id)}>
                      {busy === m.id ? 'Dispatching…' : 'Dispatch driver'}
                    </button>
                  )}
                </div>
              </li>
            ))}
        </ol>
      )}
    </>
  );
}
