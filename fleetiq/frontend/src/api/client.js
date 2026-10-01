import axios from 'axios';

export const USE_MOCKS = import.meta.env.VITE_USE_MOCKS === 'true';
const TOKEN_KEY = 'fleetiq.token';

export const tokenStore = {
  get: () => localStorage.getItem(TOKEN_KEY),
  set: (t) => localStorage.setItem(TOKEN_KEY, t),
  clear: () => localStorage.removeItem(TOKEN_KEY),
};

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 15000,
});

// Attach the JWT issued by Spring Security to every request
http.interceptors.request.use((config) => {
  const token = tokenStore.get();
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

// On 401 drop the token and send the user back to sign in
http.interceptors.response.use(
  (res) => res,
  (error) => {
    if (error.response?.status === 401) {
      tokenStore.clear();
      if (!window.location.pathname.startsWith('/login')) window.location.assign('/login');
    }
    const message =
      error.response?.data?.message ||
      (error.code === 'ECONNABORTED' ? 'The server took too long to respond. Try again.' : null) ||
      (!error.response ? 'Cannot reach the server. Check that the backend is running.' : 'Request failed.');
    return Promise.reject(new Error(message));
  }
);

export default http;
