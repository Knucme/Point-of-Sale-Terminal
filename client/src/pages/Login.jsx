import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

export default function Login() {
  const { login } = useAuth();
  const navigate = useNavigate();

  const [form, setForm]       = useState({ username: '', password: '' });
  const [error, setError]     = useState('');
  const [loading, setLoading] = useState(false);
  const [logoutReason, setLogoutReason] = useState('');

  // Check if we were redirected here due to inactivity
  useEffect(() => {
    const reason = localStorage.getItem('sos_logout_reason');
    if (reason === 'inactivity') {
      setLogoutReason('SESSION TERMINATED — INACTIVITY TIMEOUT');
    }
    localStorage.removeItem('sos_logout_reason');
  }, []);

  const handleChange = (e) =>
    setForm((prev) => ({ ...prev, [e.target.name]: e.target.value }));

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    try {
      const user = await login(form.username.trim(), form.password);
      navigate(`/${user.role.toLowerCase()}`, { replace: true });
    } catch (err) {
      setError(err.response?.data?.error || 'Authentication failed. Verify credentials.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-tac-black flex items-center justify-center p-4">
      <div className="w-full max-w-sm">
        {/* Header */}
        <div className="text-center mb-10">
          <div className="inline-block border border-tac-green/30 px-6 py-3 mb-3">
            <h1 className="text-5xl font-black text-tac-green font-mono tracking-[0.2em]">SOS</h1>
          </div>
          <p className="text-tac-muted mt-2 text-xs tracking-[0.3em] uppercase font-mono">Support of Sale // Terminal</p>
        </div>

        {/* Login card */}
        <div className="tac-panel p-8 shadow-tac">
          {/* Status bar */}
          <div className="flex items-center gap-2 mb-6 pb-3 border-b border-tac-border">
            <span className="w-2 h-2 rounded-none bg-tac-green animate-pulse-green"></span>
            <span className="font-mono text-xs text-tac-green tracking-widest uppercase">System Online — Authenticate</span>
          </div>

          {logoutReason && (
            <div className="bg-tac-amber/10 border border-tac-amber/30 rounded-tac px-4 py-3 text-amber-300 text-xs font-mono mb-4 tracking-wide">
              {logoutReason}
            </div>
          )}

          <form onSubmit={handleSubmit} className="space-y-5">
            <div>
              <label htmlFor="username" className="block text-xs text-tac-muted mb-1.5 font-mono uppercase tracking-widest">
                Operator ID
              </label>
              <input
                id="username"
                name="username"
                type="text"
                autoComplete="username"
                autoCapitalize="none"
                value={form.username}
                onChange={handleChange}
                required
                disabled={loading}
                className="w-full tac-input px-4 py-3.5 text-lg disabled:opacity-50"
                placeholder="username"
              />
            </div>

            <div>
              <label htmlFor="password" className="block text-xs text-tac-muted mb-1.5 font-mono uppercase tracking-widest">
                Access Key
              </label>
              <input
                id="password"
                name="password"
                type="password"
                autoComplete="current-password"
                value={form.password}
                onChange={handleChange}
                required
                disabled={loading}
                className="w-full tac-input px-4 py-3.5 text-lg disabled:opacity-50"
                placeholder="password"
              />
            </div>

            {/* Error message */}
            {error && (
              <div
                role="alert"
                className="bg-tac-red/10 border border-tac-red/30 rounded-tac px-4 py-3 text-red-400 text-xs font-mono tracking-wide"
              >
                [ERR] {error}
              </div>
            )}

            <button
              type="submit"
              disabled={loading}
              className="w-full tac-btn py-4 text-base mt-2 min-h-touch disabled:opacity-40 disabled:cursor-not-allowed"
            >
              {loading ? 'AUTHENTICATING...' : 'AUTHENTICATE'}
            </button>
          </form>
        </div>

        {/* Demo accounts */}
        <div className="tac-panel p-5 mt-5 shadow-tac">
          <p className="font-mono text-[10px] text-tac-muted uppercase tracking-[0.2em] mb-3 text-center">Demo Accounts</p>
          <div className="space-y-2">
            {[
              { role: 'Manager', user: 'manager', pass: 'Manager-Dev-2026', color: 'text-tac-green' },
              { role: 'Kitchen (BOH)', user: 'boh_cook', pass: 'BohCook-Dev-2026', color: 'text-tac-cyan' },
              { role: 'Server (FOH)', user: 'foh_server', pass: 'FohSvr-Dev-2026', color: 'text-tac-amber' },
            ].map((acct) => (
              <button
                key={acct.user}
                type="button"
                onClick={() => { setForm({ username: acct.user, password: acct.pass }); setError(''); }}
                className="w-full flex items-center justify-between px-3 py-2.5 rounded-tac bg-tac-mid/50 border border-tac-border hover:border-tac-green/40 hover:bg-tac-mid transition text-left group"
              >
                <div>
                  <span className={`font-mono font-bold text-sm ${acct.color}`}>{acct.role}</span>
                  <span className="text-tac-muted text-xs font-mono ml-2">{acct.user}</span>
                </div>
                <span className="text-tac-muted text-[10px] font-mono uppercase tracking-wider group-hover:text-tac-green transition">Fill</span>
              </button>
            ))}
          </div>
          <p className="text-tac-muted text-[10px] font-mono mt-3 text-center tracking-wide">
            Tap a role to auto-fill credentials
          </p>
        </div>

        <p className="text-center text-tac-muted text-[10px] mt-6 font-mono tracking-widest uppercase">
          Auto-logout after 10 min idle
        </p>
      </div>
    </div>
  );
}
