// Road-sign style plate showing the live demand multiplier.
export default function SurgePlate({ multiplier, size = 'md' }) {
  const level = multiplier >= 1.4 ? 'high' : multiplier > 1.05 ? 'rising' : 'normal';
  const label = level === 'high' ? 'High demand' : level === 'rising' ? 'Demand rising' : 'Normal price';
  return (
    <span className={`plate plate--${level} plate--${size}`} title={`${label}: ${multiplier.toFixed(2)}× base rate`}>
      <span className="plate__value">{multiplier.toFixed(2)}×</span>
      <span className="plate__label">{label}</span>
    </span>
  );
}
