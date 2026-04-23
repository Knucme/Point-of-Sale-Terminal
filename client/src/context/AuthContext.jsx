import React, { createContext, useContext, useState, useEffect, useCallback, useRef } from 'react';
import axios from 'axios';
import { connectSocket, disconnectSocket } from '../socket';

const AuthContext = createContext(null);

// Inactivity timeout in ms — configurable via env variable (default 10 min)
const INACTIVITY_MS = parseInt(import.meta.env.VITE_INACTIVITY_TIMEOUT_MS || '600000', 10);

// How often to silently refresh the JWT (refresh before expiry, default 8 min)
const REFRESH_INTERVAL_MS = parseInt(import.meta.env.VITE_TOKEN_REFRESH_MS || '480000', 10);

export const AuthProvider = ({ children }) => {
  const [user, setUser]     = useState(null);
  const [token, setToken]   = useState(null);
  const [loading, setLoading] = useState(true);

  const inactivityTimer = useRef(null);
  const refreshTimer    = useRef(null);

  // ── Logout ──────────────────────────────────────────────────────────────
  const logout = useCallback((reason) => {
    setUser(null);
    setToken(null);
    localStorage.removeItem('sos_token');
    if (reason) localStorage.setItem('sos_logout_reason', reason);
    disconnectSocket();
    clearTimeout(inactivityTimer.current);
    clearInterval(refreshTimer.current);
  }, []);

  // ── Inactivity reset ────────────────────────────────────────────────────
  const resetInactivityTimer = useCallback(() => {
    clearTimeout(inactivityTimer.current);
    inactivityTimer.current = setTimeout(() => logout('inactivity'), INACTIVITY_MS);
  }, [logout]);

  // ── Silent token refresh ─────────────────────────────────────────────────
  const startRefreshCycle = useCallback((currentToken) => {
    clearInterval(refreshTimer.current);
    refreshTimer.current = setInterval(async () => {
      try {
        const res = await axios.post(
          '/api/auth/refresh',
          {},
          { headers: { Authorization: `Bearer ${currentToken}` } }
        );
        const newToken = res.data.token;
        setToken(newToken);
        localStorage.setItem('sos_token', newToken);
        // Update axios default for subsequent calls
        axios.defaults.headers.common['Authorization'] = `Bearer ${newToken}`;
        currentToken = newToken; // update closure reference
      } catch {
        logout(); // If refresh fails, session is dead
      }
    }, REFRESH_INTERVAL_MS);
  }, [logout]);

  // ── Login ────────────────────────────────────────────────────────────────
  const login = useCallback(async (username, password) => {
    const res = await axios.post('/api/auth/login', { username, password });
    const { token: newToken, user: newUser } = res.data;

    localStorage.setItem('sos_token', newToken);
    axios.defaults.headers.common['Authorization'] = `Bearer ${newToken}`;

    setToken(newToken);
    setUser(newUser);

    connectSocket(newToken);
    resetInactivityTimer();
    startRefreshCycle(newToken);

    return newUser;
  }, [resetInactivityTimer, startRefreshCycle]);

  // ── Restore session on page load ─────────────────────────────────────────
  useEffect(() => {
    const stored = localStorage.getItem('sos_token');
    if (!stored) {
      setLoading(false);
      return;
    }

    axios.defaults.headers.common['Authorization'] = `Bearer ${stored}`;

    axios.get('/api/auth/me')
      .then((res) => {
        setToken(stored);
        setUser(res.data.user);
        connectSocket(stored);
        resetInactivityTimer();
        startRefreshCycle(stored);
      })
      .catch(() => {
        localStorage.removeItem('sos_token');
        delete axios.defaults.headers.common['Authorization'];
      })
      .finally(() => setLoading(false));
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  // ── Inactivity event listeners ───────────────────────────────────────────
  useEffect(() => {
    if (!user) return;
    const events = ['mousedown', 'keydown', 'touchstart', 'scroll', 'pointermove'];
    events.forEach((e) => window.addEventListener(e, resetInactivityTimer, { passive: true }));
    return () => events.forEach((e) => window.removeEventListener(e, resetInactivityTimer));
  }, [user, resetInactivityTimer]);

  // ── Cleanup on unmount ───────────────────────────────────────────────────
  useEffect(() => {
    return () => {
      clearTimeout(inactivityTimer.current);
      clearInterval(refreshTimer.current);
    };
  }, []);

  return (
    <AuthContext.Provider value={{ user, token, login, logout, loading }}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within an AuthProvider');
  return ctx;
};
