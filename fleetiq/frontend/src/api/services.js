import http, { USE_MOCKS } from './client';
import { mock } from './mock';

// Every call goes to the Spring Boot API, or to the mock when VITE_USE_MOCKS=true.
const call = (real, fake) => (USE_MOCKS ? fake() : real().then((r) => r.data));

export const authApi = {
  login: (body) => call(() => http.post('/auth/login', body), () => mock.login(body)),
  register: (body) => call(() => http.post('/auth/register', body), () => mock.register(body)),
  me: () => call(() => http.get('/auth/me'), () => mock.me()),
};

export const vehicleApi = {
  search: (params) => call(() => http.get('/vehicles/search', { params }), () => mock.searchVehicles(params)),
  get: (id) => call(() => http.get(`/vehicles/${id}`), () => mock.getVehicle(id)),
};

export const pricingApi = {
  quote: (params) => call(() => http.get('/pricing/quote', { params }), () => mock.getQuote(params)),
};

export const bookingApi = {
  create: (body) => call(() => http.post('/bookings', body), () => mock.createBooking(body)),
  mine: () => call(() => http.get('/bookings/me'), () => mock.myBookings()),
  cancel: (id) => call(() => http.post(`/bookings/${id}/cancel`), () => mock.cancelBooking(id)),
  late: () => call(() => http.get('/bookings/late'), () => mock.lateReturns()),
};

export const paymentApi = {
  create: (body) => call(() => http.post('/payments', body), () => mock.createPayment(body)),
  confirm: (paymentId) => call(() => http.post(`/payments/${paymentId}/confirm`), () => mock.confirmPayment(paymentId)),
};

export const predictionApi = {
  demand: (params) => call(() => http.get('/predictions/demand', { params }), () => mock.demandForecast(params)),
  maintenance: () => call(() => http.get('/predictions/maintenance'), () => mock.maintenancePredictions()),
  scheduleMaintenance: (vehicleId) =>
    call(() => http.post(`/maintenance/${vehicleId}/schedule`), () => mock.scheduleMaintenance(vehicleId)),
};

export const riskApi = {
  customers: () => call(() => http.get('/risk/customers'), () => mock.customerRisk()),
};

export const analyticsApi = {
  utilization: () => call(() => http.get('/analytics/utilization'), () => mock.utilization()),
};

export const allocationApi = {
  recommendations: () => call(() => http.get('/allocation/recommendations'), () => mock.allocationRecommendations()),
  apply: (id) => call(() => http.post(`/allocation/${id}/apply`), () => mock.applyAllocation(id)),
};

export const notificationApi = {
  list: () => call(() => http.get('/notifications'), () => mock.notifications()),
  markRead: (id) => call(() => http.patch(`/notifications/${id}/read`), async () => ({ id })),
};
