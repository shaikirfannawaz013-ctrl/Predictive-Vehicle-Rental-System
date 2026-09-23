const inr = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 });
export const money = (n) => inr.format(n ?? 0);
export const pct = (n) => `${Math.round((n ?? 0) * 100)}%`;
export const dateTime = (iso) =>
  new Date(iso).toLocaleString('en-IN', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' });
export const timeAgo = (iso) => {
  const mins = Math.round((Date.now() - new Date(iso)) / 6e4);
  if (mins < 1) return 'just now';
  if (mins < 60) return `${mins} min ago`;
  const h = Math.round(mins / 60);
  return h < 24 ? `${h} h ago` : `${Math.round(h / 24)} d ago`;
};
// Value for <input type="datetime-local">
export const toLocalInput = (d) => {
  const off = d.getTimezoneOffset() * 6e4;
  return new Date(d - off).toISOString().slice(0, 16);
};
export const VEHICLE_TYPES = ['SUV', 'SEDAN', 'HATCHBACK', 'EV', 'BIKE'];
export const typeLabel = { SUV: 'SUV', SEDAN: 'Sedan', HATCHBACK: 'Hatchback', EV: 'Electric', BIKE: 'Two-wheeler' };
