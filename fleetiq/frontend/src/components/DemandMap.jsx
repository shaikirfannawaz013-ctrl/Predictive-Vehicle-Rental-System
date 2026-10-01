import SurgePlate from './SurgePlate';

// Schematic map of pickup zones around Tirupati. Circle size = searches, colour = predicted demand.
export default function DemandMap({ zones, selected, onSelect }) {
  const colorFor = (d) => (d >= 0.75 ? 'var(--stop)' : d >= 0.5 ? 'var(--amber)' : 'var(--go)');
  const links = [['TML', 'TPT-C'], ['TPT-C', 'REN'], ['REN', 'SKH'], ['TPT-C', 'CHG'], ['TPT-C', 'PTR']];
  const pos = Object.fromEntries(zones.map((z) => [z.id, z]));

  return (
    <div className="demand-map">
      <svg viewBox="0 0 100 100" preserveAspectRatio="xMidYMid meet" role="img" aria-label="Predicted demand by pickup zone">
        <defs>
          <pattern id="grid" width="10" height="10" patternUnits="userSpaceOnUse">
            <path d="M 10 0 L 0 0 0 10" fill="none" stroke="var(--map-grid)" strokeWidth="0.2" />
          </pattern>
        </defs>
        <rect width="100" height="100" fill="url(#grid)" />
        {links.map(([a, b]) =>
          pos[a] && pos[b] ? (
            <line key={a + b} x1={pos[a].x} y1={pos[a].y} x2={pos[b].x} y2={pos[b].y} className="demand-map__road" />
          ) : null
        )}
        {zones.map((z) => {
          const r = 3 + Math.sqrt(z.searches) * 1.1;
          const active = selected === z.id;
          return (
            <g
              key={z.id}
              className={`demand-map__zone ${active ? 'is-active' : ''}`}
              onClick={() => onSelect?.(z.id)}
              onKeyDown={(e) => (e.key === 'Enter' || e.key === ' ') && onSelect?.(z.id)}
              tabIndex={onSelect ? 0 : -1}
              role={onSelect ? 'button' : undefined}
              aria-label={`${z.name}: ${Math.round(z.demand * 100)}% predicted demand, ${z.searches} searches`}
            >
              <circle cx={z.x} cy={z.y} r={r} fill={colorFor(z.demand)} fillOpacity="0.22" />
              <circle cx={z.x} cy={z.y} r="1.6" fill={colorFor(z.demand)} />
              <text x={z.x} y={z.y + r + 3.6} textAnchor="middle" className="demand-map__label">{z.name}</text>
            </g>
          );
        })}
      </svg>
      <ul className="demand-map__legend">
        {zones
          .slice()
          .sort((a, b) => b.demand - a.demand)
          .map((z) => (
            <li key={z.id} className={selected === z.id ? 'is-active' : ''}>
              <button type="button" onClick={() => onSelect?.(z.id)} disabled={!onSelect}>
                <span className="demand-map__name">{z.name}</span>
                <span className="muted small">{z.searches} searches · {z.supply} vehicles</span>
              </button>
              <SurgePlate multiplier={z.multiplier} size="sm" />
            </li>
          ))}
      </ul>
    </div>
  );
}
