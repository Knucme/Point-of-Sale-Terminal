import { io } from 'socket.io-client';

let socket = null;

// Track the most recent alert timestamp so we can catch up after reconnect
const LAST_ALERT_KEY = 'sos_last_alert_ts';

/** Record the timestamp of the most recent alert we've seen. */
export const recordAlertTimestamp = (ts) => {
  const current = localStorage.getItem(LAST_ALERT_KEY);
  if (!current || new Date(ts) > new Date(current)) {
    localStorage.setItem(LAST_ALERT_KEY, ts);
  }
};

/**
 * Create (or replace) the Socket.io connection, authenticated with a JWT.
 * The Vite dev proxy forwards /socket.io → localhost:3001.
 */
export const connectSocket = (token) => {
  if (socket) {
    socket.disconnect();
    socket = null;
  }

  // In dev, Vite proxies /socket.io → localhost:3002.
  // In production, connect directly to the Socket.IO port on the same host.
  const socketUrl = import.meta.env.VITE_SOCKET_URL || '/';

  socket = io(socketUrl, {
    query: { token },
    reconnection: true,
    reconnectionDelay: 1000,
    reconnectionAttempts: 10,
    // Prefer WebSocket; fall back to polling if WS unavailable
    transports: ['websocket', 'polling'],
  });

  socket.on('connect', () => {
    console.log('[Socket] Connected:', socket.id);
    // Request missed alerts since last seen timestamp
    const since = localStorage.getItem(LAST_ALERT_KEY);
    socket.emit('alerts:catchup', { since: since || null });
  });

  socket.on('connect_error', (err) => {
    console.warn('[Socket] Connection error:', err.message);
    // In production, Socket.IO may not be reachable on free-tier hosting.
    // The app works fully without it — you just won't get real-time updates.
  });

  socket.on('disconnect', (reason) => {
    console.warn('[Socket] Disconnected:', reason);
  });

  return socket;
};

export const disconnectSocket = () => {
  if (socket) {
    socket.disconnect();
    socket = null;
  }
};

/** Returns the active socket instance, or null if not connected. */
export const getSocket = () => socket;
