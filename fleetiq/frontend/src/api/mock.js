// Mock backend used when VITE_USE_MOCKS=true. Shapes match the REST contract in README.md.
const wait = (ms = 350) => new Promise((r) => setTimeout(r, ms + Math.random() * 250));

export const ZONES = [
  { id: 'TPT-C', name: 'Tirupati Central', x: 48, y: 52, demand: 0.92, supply: 14, searches: 38 },
  { id: 'TML', name: 'Tirumala', x: 30, y: 20, demand: 0.81, supply: 6, searches: 24 },
  { id: 'REN', name: 'Renigunta Airport', x: 74, y: 44, demand: 0.67, supply: 9, searches: 19 },
  { id: 'CHG', name: 'Chandragiri', x: 22, y: 70, demand: 0.34, supply: 8, searches: 7 },
  { id: 'SKH', name: 'Srikalahasti', x: 88, y: 22, demand: 0.48, supply: 5, searches: 11 },
  { id: 'PTR', name: 'Puttur', x: 70, y: 84, demand: 0.21, supply: 7, searches: 4 },
];

const multiplierFor = (demand) => Math.round((1 + Math.max(0, demand - 0.4) * 1.1) * 100) / 100;

function daysFromNow(d) { return new Date(Date.now() + d * 864e5).toISOString(); }
function hoursFromNow(h) { return new Date(Date.now() + h * 36e5).toISOString(); }

let vehicles = [
  { id: 1, name: 'Mahindra XUV700', type: 'SUV', seats: 7, fuel: 'Diesel', transmission: 'Automatic', baseRate: 4200, zoneId: 'TPT-C', status: 'AVAILABLE', odometer: 38120, healthScore: 82 },
  { id: 2, name: 'Toyota Innova Crysta', type: 'SUV', seats: 7, fuel: 'Diesel', transmission: 'Manual', baseRate: 3900, zoneId: 'TML', status: 'AVAILABLE', odometer: 91240, healthScore: 54 },
  { id: 3, name: 'Hyundai Creta', type: 'SUV', seats: 5, fuel: 'Petrol', transmission: 'Automatic', baseRate: 3300, zoneId: 'REN', status: 'RENTED', odometer: 22410, healthScore: 91 },
  { id: 4, name: 'Honda City', type: 'SEDAN', seats: 5, fuel: 'Petrol', transmission: 'Manual', baseRate: 2600, zoneId: 'TPT-C', status: 'AVAILABLE', odometer: 47300, healthScore: 76 },
  { id: 5, name: 'Maruti Dzire', type: 'SEDAN', seats: 5, fuel: 'CNG', transmission: 'Manual', baseRate: 1900, zoneId: 'CHG', status: 'AVAILABLE', odometer: 120550, healthScore: 38 },
  { id: 6, name: 'Maruti Swift', type: 'HATCHBACK', seats: 5, fuel: 'Petrol', transmission: 'Manual', baseRate: 1500, zoneId: 'PTR', status: 'AVAILABLE', odometer: 30210, healthScore: 88 },
  { id: 7, name: 'Tata Nexon EV', type: 'EV', seats: 5, fuel: 'Electric', transmission: 'Automatic', baseRate: 2800, zoneId: 'TPT-C', status: 'MAINTENANCE', odometer: 18900, healthScore: 61 },
  { id: 8, name: 'MG ZS EV', type: 'EV', seats: 5, fuel: 'Electric', transmission: 'Automatic', baseRate: 3100, zoneId: 'REN', status: 'AVAILABLE', odometer: 12050, healthScore: 94 },
  { id: 9, name: 'Royal Enfield Classic 350', type: 'BIKE', seats: 2, fuel: 'Petrol', transmission: 'Manual', baseRate: 900, zoneId: 'TML', status: 'AVAILABLE', odometer: 15320, healthScore: 71 },
  { id: 10, name: 'Honda Activa 6G', type: 'BIKE', seats: 2, fuel: 'Petrol', transmission: 'Automatic', baseRate: 450, zoneId: 'SKH', status: 'AVAILABLE', odometer: 26700, healthScore: 66 },
  { id: 11, name: 'Kia Seltos', type: 'SUV', seats: 5, fuel: 'Diesel', transmission: 'Automatic', baseRate: 3400, zoneId: 'CHG', status: 'AVAILABLE', odometer: 55800, healthScore: 69 },
  { id: 12, name: 'Hyundai i20', type: 'HATCHBACK', seats: 5, fuel: 'Petrol', transmission: 'Automatic', baseRate: 1800, zoneId: 'TPT-C', status: 'RENTED', odometer: 41200, healthScore: 80 },
];

let bookings = [
  { id: 1001, vehicleId: 3, vehicleName: 'Hyundai Creta', customer: 'Ravi Kumar', startTime: daysFromNow(-2), endTime: daysFromNow(1), total: 11880, status: 'ACTIVE', paymentStatus: 'PAID' },
  { id: 1002, vehicleId: 12, vehicleName: 'Hyundai i20', customer: 'Anitha Reddy', startTime: daysFromNow(-3), endTime: hoursFromNow(-5), total: 5400, status: 'ACTIVE', paymentStatus: 'PAID' },
];

const zoneOf = (id) => ZONES.find((z) => z.id === id);

function withPricing(v) {
  const zone = zoneOf(v.zoneId);
  const m = multiplierFor(zone.demand);
  return { ...v, zoneName: zone.name, demandMultiplier: m, currentRate: Math.round(v.baseRate * m) };
}

export const mock = {
  async login({ email }) {
    await wait();
    const isAdmin = email.toLowerCase().includes('admin');
    return {
      token: 'mock.jwt.token',
      user: { id: isAdmin ? 1 : 2, name: isAdmin ? 'Fleet Manager' : 'Irfan', email, role: isAdmin ? 'ADMIN' : 'CUSTOMER' },
    };
  },
  async register(data) {
    await wait();
    return { token: 'mock.jwt.token', user: { id: 3, name: data.name, email: data.email, role: 'CUSTOMER' } };
  },
  async me() {
    await wait(100);
    const raw = localStorage.getItem('fleetiq.mockUser');
    if (!raw) throw new Error('Not signed in');
    return JSON.parse(raw);
  },

  async searchVehicles({ zoneId, type }) {
    await wait();
    return vehicles
      .filter((v) => v.status === 'AVAILABLE')
      .filter((v) => !type || v.type === type)
      .filter((v) => !zoneId || v.zoneId === zoneId)
      .map(withPricing)
      .map((v) => ({ ...v, availabilityProbability: Math.max(0.15, Math.round((1 - zoneOf(v.zoneId).demand * 0.7) * 100) / 100) }));
  },
  async getVehicle(id) {
    await wait();
    const v = vehicles.find((x) => x.id === Number(id));
    if (!v) throw new Error('Vehicle not found.');
    return withPricing(v);
  },
  async getQuote({ vehicleId, startTime, endTime }) {
    await wait(200);
    const v = withPricing(vehicles.find((x) => x.id === Number(vehicleId)));
    const days = Math.max(1, Math.ceil((new Date(endTime) - new Date(startTime)) / 864e5));
    const weekend = [0, 5, 6].includes(new Date(startTime).getDay());
    const multiplier = Math.round((v.demandMultiplier + (weekend ? 0.1 : 0)) * 100) / 100;
    const subtotal = Math.round(v.baseRate * multiplier * days);
    const zone = zoneOf(v.zoneId);
    const reasons = [];
    if (zone.demand > 0.6) reasons.push(`${zone.searches} people searched near ${zone.name} in the last hour`);
    if (weekend) reasons.push('Weekend pickup');
    return { days, baseRate: v.baseRate, multiplier, subtotal, deposit: 2000, tax: Math.round(subtotal * 0.18), total: Math.round(subtotal * 1.18), reasons };
  },

  async createBooking({ vehicleId, startTime, endTime }) {
    await wait();
    const q = await mock.getQuote({ vehicleId, startTime, endTime });
    const v = vehicles.find((x) => x.id === Number(vehicleId));
    const b = { id: 1000 + bookings.length + 1, vehicleId: v.id, vehicleName: v.name, customer: 'You', startTime, endTime, total: q.total, status: 'PENDING_PAYMENT', paymentStatus: 'PENDING' };
    bookings.push(b);
    return b;
  },
  async myBookings() {
    await wait();
    return [...bookings].reverse();
  },
  async cancelBooking(id) {
    await wait();
    bookings = bookings.map((b) => (b.id === id ? { ...b, status: 'CANCELLED' } : b));
    return bookings.find((b) => b.id === id);
  },
  async createPayment({ bookingId, method }) {
    await wait();
    return { paymentId: `pay_${bookingId}`, bookingId, method, status: 'CREATED' };
  },
  async confirmPayment(paymentId) {
    await wait(700);
    const id = Number(paymentId.split('_')[1]);
    const booking = bookings.find((b) => b.id === id);
    bookings = bookings.map((b) => (b.id === id ? { ...b, status: 'CONFIRMED', paymentStatus: 'PAID' } : b));
    vehicles = vehicles.map((v) => (v.id === booking?.vehicleId ? { ...v, status: 'RENTED' } : v));
    return { paymentId, status: 'SUCCESS' };
  },

  async demandForecast() {
    await wait();
    const days = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
    const forecast = days.map((d, i) => ({
      day: d,
      SUV: Math.round(18 + (i >= 4 ? 16 : 0) + Math.random() * 5),
      SEDAN: Math.round(14 + (i >= 4 ? 6 : 0) + Math.random() * 4),
      HATCHBACK: Math.round(10 + Math.random() * 4),
      EV: Math.round(5 + i * 0.6),
      BIKE: Math.round(12 + (i >= 5 ? 5 : 0)),
    }));
    return { zones: ZONES.map((z) => ({ ...z, multiplier: multiplierFor(z.demand) })), forecast, modelVersion: 'demand-xgb-v3', generatedAt: new Date().toISOString() };
  },
  async maintenancePredictions() {
    await wait();
    return vehicles
      .map((v) => ({
        vehicleId: v.id,
        vehicleName: v.name,
        status: v.status,
        odometer: v.odometer,
        healthScore: v.healthScore,
        failureProbability: Math.round((1 - v.healthScore / 100) * 100) / 100,
        component: v.healthScore < 45 ? 'Brake pads' : v.healthScore < 60 ? 'Clutch plate' : v.healthScore < 75 ? 'Engine oil' : 'Tyres',
        dueInDays: Math.max(1, Math.round(v.healthScore / 4)),
      }))
      .sort((a, b) => a.healthScore - b.healthScore);
  },
  async scheduleMaintenance(vehicleId) {
    await wait();
    vehicles = vehicles.map((v) => (v.id === vehicleId ? { ...v, status: 'MAINTENANCE' } : v));
    return { vehicleId, status: 'SCHEDULED' };
  },
  async customerRisk() {
    await wait();
    return [
      { customerId: 21, name: 'Suresh Babu', totalBookings: 14, lateReturns: 5, damageClaims: 1, paymentFailures: 2, score: 78 },
      { customerId: 22, name: 'Anitha Reddy', totalBookings: 9, lateReturns: 3, damageClaims: 0, paymentFailures: 0, score: 52 },
      { customerId: 23, name: 'Ravi Kumar', totalBookings: 22, lateReturns: 1, damageClaims: 0, paymentFailures: 0, score: 14 },
      { customerId: 24, name: 'Meena Iyer', totalBookings: 3, lateReturns: 0, damageClaims: 1, paymentFailures: 1, score: 41 },
      { customerId: 25, name: 'Karthik Naidu', totalBookings: 31, lateReturns: 0, damageClaims: 0, paymentFailures: 0, score: 6 },
    ];
  },
  async lateReturns() {
    await wait();
    return bookings
      .filter((b) => b.status === 'ACTIVE' && new Date(b.endTime) < new Date())
      .map((b) => {
        const hrs = Math.round((Date.now() - new Date(b.endTime)) / 36e5);
        return { ...b, overdueHours: hrs, penalty: hrs * 250 };
      });
  },
  async utilization() {
    await wait();
    const types = ['SUV', 'SEDAN', 'HATCHBACK', 'EV', 'BIKE'];
    return {
      fleetSize: vehicles.length,
      rented: vehicles.filter((v) => v.status === 'RENTED').length,
      inMaintenance: vehicles.filter((v) => v.status === 'MAINTENANCE').length,
      utilizationRate: 0.68,
      revenueToday: 48250,
      byType: types.map((t, i) => ({ type: t, utilization: [84, 66, 58, 47, 72][i] })),
      trend: Array.from({ length: 14 }, (_, i) => ({
        date: new Date(Date.now() - (13 - i) * 864e5).toLocaleDateString('en-IN', { day: '2-digit', month: 'short' }),
        utilization: Math.round(52 + i * 1.3 + Math.sin(i) * 6),
        revenue: Math.round(32000 + i * 1100 + Math.cos(i) * 4000),
      })),
    };
  },
  async allocationRecommendations() {
    await wait();
    return [
      { id: 'A1', vehicleId: 11, vehicleName: 'Kia Seltos', type: 'SUV', fromZone: 'Chandragiri', toZone: 'Tirupati Central', expectedGain: 5100, reason: 'SUV searches are 3× normal for this weekend in Tirupati Central' },
      { id: 'A2', vehicleId: 6, vehicleName: 'Maruti Swift', type: 'HATCHBACK', fromZone: 'Puttur', toZone: 'Renigunta Airport', expectedGain: 2300, reason: '4 airport requests went unmet yesterday morning' },
      { id: 'A3', vehicleId: 10, vehicleName: 'Honda Activa 6G', type: 'BIKE', fromZone: 'Srikalahasti', toZone: 'Tirumala', expectedGain: 900, reason: 'Bike demand from pilgrims peaks on Saturday' },
    ];
  },
  async applyAllocation(id) {
    await wait();
    return { id, status: 'DISPATCHED' };
  },
  async notifications() {
    await wait(150);
    return [
      { id: 'n1', type: 'DEMAND_SURGE', message: 'SUV demand in Tirupati Central is 2.1× normal. Prices raised to 1.57×.', createdAt: new Date(Date.now() - 6e5).toISOString(), read: false },
      { id: 'n2', type: 'LATE_RETURN', message: 'Booking #1002 (Hyundai i20) is 5 hours overdue.', createdAt: new Date(Date.now() - 18e5).toISOString(), read: false },
      { id: 'n3', type: 'MAINTENANCE', message: 'Maruti Dzire brake pads likely to fail within 10 days.', createdAt: new Date(Date.now() - 72e5).toISOString(), read: true },
    ];
  },
};

// Simulates the Kafka → SSE notification stream
const LIVE = [
  { type: 'DEMAND_SURGE', message: '12 new SUV searches near Renigunta Airport. Multiplier now 1.40×.' },
  { type: 'BOOKING', message: 'New booking confirmed for Mahindra XUV700.' },
  { type: 'LATE_RETURN', message: 'Booking #1001 return window closes in 30 minutes.' },
  { type: 'MAINTENANCE', message: 'Toyota Innova Crysta clutch wear crossed threshold.' },
];
export function mockStream(onEvent) {
  let i = 0;
  const t = setInterval(() => {
    const e = LIVE[i++ % LIVE.length];
    onEvent({ ...e, id: `live-${Date.now()}`, createdAt: new Date().toISOString(), read: false });
  }, 25000);
  return () => clearInterval(t);
}
