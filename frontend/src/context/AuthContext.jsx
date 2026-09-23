import { createContext, useContext, useEffect, useState } from 'react';
import { authApi } from '../api/services';
import { tokenStore, USE_MOCKS } from '../api/client';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    if (!tokenStore.get()) return setReady(true);
    authApi
      .me()
      .then(setUser)
      .catch(() => tokenStore.clear())
      .finally(() => setReady(true));
  }, []);

  const persist = ({ token, user }) => {
    tokenStore.set(token);
    if (USE_MOCKS) localStorage.setItem('fleetiq.mockUser', JSON.stringify(user));
    setUser(user);
    return user;
  };

  const value = {
    user,
    ready,
    isAdmin: user?.role === 'ADMIN',
    login: (creds) => authApi.login(creds).then(persist),
    register: (data) => authApi.register(data).then(persist),
    logout: () => {
      tokenStore.clear();
      localStorage.removeItem('fleetiq.mockUser');
      setUser(null);
    },
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export const useAuth = () => useContext(AuthContext);
