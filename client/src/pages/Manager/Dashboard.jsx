import React, { useEffect, useState, useCallback } from 'react';
import axios from 'axios';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { getSocket, recordAlertTimestamp } from '../../socket';
import StatusBadge from '../../components/StatusBadge';

// ── Summary card component ──────────────────────────────────────────────────
function SummaryCard({ label, value, tag, color }) {
  return (
    <div className="tac-panel p-6">
      <div className="text-tac-muted font-mono text-[10px] uppercase tracking-[0.2em] mb-2">{tag}</div>
      <div className={`text-3xl font-black font-mono ${color}`}>{value}</div>
      <div className="text-tac-muted text-sm mt-1">{label}</div>
    </div>
  );
}

// ── Date range helpers ──────────────────────────────────────────────────────
const toISO = (d) => d.toISOString().split('T')[0];
const today = () => toISO(new Date());
const daysAgo = (n) => { const d = new Date(); d.setDate(d.getDate() - n); return toISO(d); };

export default function ManagerDashboard() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const [summary, setSummary] = useState(null);
  const [loadingSum, setLoadingSum] = useState(true);
  const [terminals, setTerminals] = useState([]);
  const [bohAlerts, setBohAlerts] = useState([]);
  const [lowStockItems, setLowStockItems] = useState([]);
  const [lastRefresh, setLastRefresh] = useState(null);

  // Active orders modal
  const [showActiveOrders, setShowActiveOrders] = useState(false);
  const [activeOrdersList, setActiveOrdersList] = useState([]);
  const [loadingActiveOrders, setLoadingActiveOrders] = useState(false);

  // Date-range comparison (UC-07 step 4)
  const [compareMode, setCompareMode] = useState(false);
  const [compareFrom, setCompareFrom] = useState(daysAgo(14));
  const [compareTo, setCompareTo] = useState(daysAgo(7));
  const [compareSummary, setCompareSummary] = useState(null);
  const [loadingCompare, setLoadingCompare] = useState(false);

  // ── Fetch live summary + inventory ──────────────────────────────────────
  const fetchSummary = useCallback(async () => {
    try {
      const [salesRes, invRes] = await Promise.all([
        axios.get('/api/sales/summary'),
        axios.get('/api/inventory'),
      ]);
      setSummary(salesRes.data);
      setLowStockItems((invRes.data || []).filter((i) => Number(i.quantity) <= Number(i.lowStockThreshold)));
      setLastRefresh(new Date());
    } catch (err) {
      console.error('Failed to load summary:', err);
    } finally {
      setLoadingSum(false);
    }
  }, []);

  useEffect(() => {
    fetchSummary();
    const interval = setInterval(fetchSummary, 10_000);
    return () => clearInterval(interval);
  }, [fetchSummary]);

  // ── Fetch comparison period ─────────────────────────────────────────────
  const fetchCompare = useCallback(async () => {
    setLoadingCompare(true);
    try {
      const res = await axios.get('/api/sales', { params: { from: compareFrom, to: compareTo } });
      setCompareSummary({
        revenue: res.data.totalRevenue,
        count: res.data.count,
      });
    } catch { setCompareSummary(null); }
    finally { setLoadingCompare(false); }
  }, [compareFrom, compareTo]);

  useEffect(() => {
    if (compareMode) fetchCompare();
  }, [compareMode, fetchCompare]);

  // Wait for socket to be ready
  const [socketReady, setSocketReady] = useState(!!getSocket());
  useEffect(() => {
    if (socketReady) return;
    const id = setInterval(() => { if (getSocket()) { setSocketReady(true); clearInterval(id); } }, 500);
    return () => clearInterval(id);
  }, [socketReady]);

  // ── Terminal disconnect alerts + BOH alerts ─────────────────────────────
  useEffect(() => {
    const socket = getSocket();
    if (!socket) return;
    const onDisconnect = (data) => setTerminals((prev) => [data, ...prev].slice(0, 10));
    const onBohAlert = (alert) => {
      setBohAlerts((prev) => [alert, ...prev].slice(0, 20));
      if (alert.timestamp) recordAlertTimestamp(alert.timestamp);
    };
    const onMissedAlerts = (missed) => {
      if (!Array.isArray(missed) || missed.length === 0) return;
      setBohAlerts((prev) => [...missed.reverse(), ...prev].slice(0, 30));
      const latest = missed[missed.length - 1];
      if (latest?.timestamp) recordAlertTimestamp(latest.timestamp);
    };
    socket.on('terminal:disconnected', onDisconnect);
    socket.on('alert:broadcast', onBohAlert);
    socket.on('alerts:missed', onMissedAlerts);
    return () => {
      socket.off('terminal:disconnected', onDisconnect);
      socket.off('alert:broadcast', onBohAlert);
      socket.off('alerts:missed', onMissedAlerts);
    };
  }, [socketReady]); // eslint-disable-line react-hooks/exhaustive-deps

  // Navigation tiles
  const tiles = [
    { label: 'Inventory', tag: 'INV', path: '/manager/inventory' },
    { label: 'Sales Report', tag: 'RPT', path: '/manager/sales' },
    { label: 'User Accounts', tag: 'USR', path: '/manager/users' },
    { label: 'Security Logs', tag: 'SEC', path: '/manager/security-logs' },
  ];

  return (
    <div className="min-h-screen bg-tac-darker text-tac-text p-4 md:p-6">
      {/* Header */}
      <header className="flex items-center justify-between mb-6">
        <div>
          <div className="flex items-center gap-3 mb-1">
            <span className="text-tac-green font-mono font-black text-2xl tracking-widest">SOS</span>
            <span className="text-tac-border">|</span>
            <span className="text-tac-muted font-mono text-xs uppercase tracking-widest">Command Center</span>
          </div>
          <p className="text-tac-muted text-sm font-mono">{user?.name}</p>
        </div>
        <button onClick={logout}
          className="tac-btn-danger min-h-[48px] px-5 tap-target">
          SIGN OUT
        </button>
      </header>

      {/* Active orders modal */}
      {showActiveOrders && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4">
          <div className="tac-panel shadow-tac-glow max-w-2xl w-full max-h-[80vh] flex flex-col animate-fade-in">
            <div className="px-6 pt-5 pb-3 border-b border-tac-border flex items-center justify-between">
              <h2 className="text-xl font-black text-tac-bright font-mono">Active Orders</h2>
              <button onClick={() => setShowActiveOrders(false)} className="text-tac-muted hover:text-tac-text text-2xl leading-none transition">✕</button>
            </div>
            <div className="flex-1 overflow-y-auto px-6 py-4">
              {loadingActiveOrders ? (
                <p className="text-tac-muted font-mono animate-pulse">Loading...</p>
              ) : activeOrdersList.length === 0 ? (
                <p className="text-tac-muted font-mono text-sm">No active orders right now.</p>
              ) : (
                <div className="grid gap-3 md:grid-cols-2">
                  {activeOrdersList.map((order) => {
                    const items = order.orderItems ?? [];
                    const orderTotal = items.reduce((s, i) => s + (Number(i.menuItem?.price ?? 0) * i.quantity), 0);
                    const BORDER = { PENDING: 'border-l-tac-amber', IN_PROGRESS: 'border-l-tac-cyan', DELAYED: 'border-l-tac-red' };
                    return (
                      <div key={order.id} className={`tac-panel border-l-2 ${BORDER[order.status] ?? 'border-l-tac-border'} p-4`}>
                        <div className="flex items-center justify-between mb-2">
                          <p className="text-xl font-bold text-tac-bright font-mono">TBL {order.tableNumber}</p>
                          <StatusBadge status={order.status} />
                        </div>
                        <p className="text-tac-muted text-xs font-mono mb-2">
                          ORD #{order.id} — {order.submittedBy?.name ?? `User #${order.submittedById}`}
                        </p>
                        {order.status === 'DELAYED' && order.estimatedWait && (
                          <p className="text-amber-300 text-sm font-mono font-semibold mb-2">Est. ~{order.estimatedWait} min</p>
                        )}
                        {order.bohNote && (
                          <p className="text-tac-cyan text-sm italic bg-tac-cyan/10 border border-tac-cyan/20 rounded-tac px-3 py-2 mb-2 font-mono">Kitchen: {order.bohNote}</p>
                        )}
                        <ul className="space-y-0.5">
                          {items.map((item, i) => (
                            <li key={item.id ?? i} className="text-sm text-tac-text">
                              <span className="font-bold text-tac-bright font-mono">{item.quantity}x</span>{' '}
                              {item.menuItem?.name ?? 'Unknown'}
                              {item.specialInstructions && <span className="ml-1 text-tac-muted italic">— {item.specialInstructions}</span>}
                            </li>
                          ))}
                        </ul>
                        <div className="text-right text-sm text-tac-muted mt-1 pt-1 border-t border-tac-border font-mono">
                          ${orderTotal.toFixed(2)}
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}
            </div>
          </div>
        </div>
      )}

      {/* Terminal disconnect warnings */}
      {terminals.length > 0 && (
        <div className="mb-4 bg-tac-amber/10 border border-tac-amber/30 rounded-tac p-4">
          <h3 className="font-mono font-bold text-amber-300 mb-2 text-xs uppercase tracking-widest">[!] Terminal Disconnections</h3>
          {terminals.map((t, i) => (
            <p key={i} className="text-sm text-amber-200 font-mono">
              {t.name} ({t.role}) — disconnected {new Date(t.timestamp).toLocaleTimeString()}
            </p>
          ))}
        </div>
      )}

      {/* BOH alerts */}
      {bohAlerts.length > 0 && (
        <div className="mb-4 bg-tac-cyan/10 border border-tac-cyan/30 rounded-tac p-4">
          <h3 className="font-mono font-bold text-cyan-300 mb-2 text-xs uppercase tracking-widest">BOH Alerts</h3>
          {bohAlerts.slice(0, 5).map((a, i) => (
            <p key={i} className="text-sm text-tac-text font-mono">
              <span className="text-tac-muted text-xs mr-2">{a.timestamp ? new Date(a.timestamp).toLocaleTimeString() : ''}</span>
              {a.sender?.name && <span className="text-tac-cyan font-semibold">{a.sender.name}: </span>}
              {a.message}
            </p>
          ))}
        </div>
      )}

      {/* Summary cards — clickable */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 mb-4">
        <div onClick={() => navigate('/manager/sales')} className="cursor-pointer hover:shadow-tac-glow transition rounded-tac">
          <SummaryCard label="Today's Revenue" value={loadingSum ? '...' : `$${summary?.todayRevenue ?? '0.00'}`} tag="REV" color="text-tac-green" />
        </div>
        <div onClick={() => navigate('/manager/sales')} className="cursor-pointer hover:shadow-tac-glow transition rounded-tac">
          <SummaryCard label="Orders Completed Today" value={loadingSum ? '...' : summary?.todayCompletedOrders ?? 0} tag="ORD" color="text-tac-cyan" />
        </div>
        <div onClick={async () => {
          setShowActiveOrders(true);
          setLoadingActiveOrders(true);
          try {
            const res = await axios.get('/api/orders');
            const all = Array.isArray(res.data) ? res.data : (res.data.orders ?? []);
            setActiveOrdersList(all.filter((o) => ['PENDING', 'IN_PROGRESS', 'DELAYED'].includes(o.status)));
          } catch { setActiveOrdersList([]); }
          finally { setLoadingActiveOrders(false); }
        }} className="cursor-pointer hover:shadow-tac-glow transition rounded-tac" title="Click to view active orders">
          <SummaryCard label="Active Orders" value={loadingSum ? '...' : summary?.activeOrders ?? 0} tag="ACT" color="text-tac-amber" />
        </div>
      </div>

      {/* Date comparison toggle (UC-07 step 4) */}
      <div className="mb-6">
        <button onClick={() => setCompareMode(!compareMode)}
          className="text-xs text-tac-muted hover:text-tac-green transition font-mono uppercase tracking-widest underline underline-offset-4">
          {compareMode ? 'Hide comparison' : 'Compare with a prior period'}
        </button>
        {compareMode && (
          <div className="mt-3 tac-panel p-4 animate-fade-in">
            <div className="flex items-center gap-2 flex-wrap mb-3">
              <span className="text-tac-muted text-xs font-mono uppercase">From</span>
              <input type="date" value={compareFrom} onChange={(e) => setCompareFrom(e.target.value)}
                className="tac-input px-3 py-2 text-sm" />
              <span className="text-tac-muted text-xs font-mono uppercase">to</span>
              <input type="date" value={compareTo} onChange={(e) => setCompareTo(e.target.value)}
                className="tac-input px-3 py-2 text-sm" />
            </div>
            {loadingCompare ? (
              <p className="text-tac-muted text-sm animate-pulse font-mono">Loading...</p>
            ) : compareSummary ? (
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <p className="text-tac-muted text-[10px] uppercase tracking-[0.2em] font-mono">Revenue</p>
                  <p className="text-xl font-bold text-tac-green font-mono">${compareSummary.revenue}</p>
                </div>
                <div>
                  <p className="text-tac-muted text-[10px] uppercase tracking-[0.2em] font-mono">Orders</p>
                  <p className="text-xl font-bold text-tac-cyan font-mono">{compareSummary.count}</p>
                </div>
              </div>
            ) : (
              <p className="text-tac-muted text-sm font-mono">No data for this period.</p>
            )}
          </div>
        )}
      </div>

      {/* Top items */}
      <div className="tac-panel p-6 mb-4">
        <h2 className="font-mono font-bold text-tac-green text-sm mb-4 uppercase tracking-widest">Top Selling Items (All Time)</h2>
        {loadingSum ? (
          <p className="text-tac-muted animate-pulse font-mono">Loading...</p>
        ) : summary?.topItems?.length > 0 ? (
          <ol className="space-y-2">
            {summary.topItems.map((item, i) => (
              <li key={i} className="flex justify-between text-sm text-tac-text font-mono">
                <span><span className="text-tac-muted mr-2">{String(i + 1).padStart(2, '0')}.</span> {item.menuItem?.name ?? 'Unknown'}</span>
                <span className="text-tac-muted">{item.totalQuantitySold} sold</span>
              </li>
            ))}
          </ol>
        ) : (
          <p className="text-tac-muted text-sm font-mono">No sales data yet.</p>
        )}
      </div>

      {/* Low-stock inventory panel (UC-07 Step 3) */}
      <div className="tac-panel p-6 mb-4">
        <div className="flex items-center justify-between mb-4">
          <h2 className="font-mono font-bold text-tac-green text-sm uppercase tracking-widest">Inventory Alerts</h2>
          {lastRefresh && <span className="text-tac-muted text-[10px] font-mono">Updated {lastRefresh.toLocaleTimeString()}</span>}
        </div>
        {loadingSum ? (
          <p className="text-tac-muted animate-pulse font-mono">Loading...</p>
        ) : lowStockItems.length > 0 ? (
          <div className="space-y-2">
            {lowStockItems.map((item) => {
              const qty = Number(item.quantity);
              const out = qty <= 0;
              return (
                <div key={item.id} className={`flex items-center justify-between text-sm rounded-tac px-3 py-2 ${out ? 'bg-tac-red/10 border border-tac-red/20' : 'bg-tac-amber/10 border border-tac-amber/20'}`}>
                  <span className="text-tac-bright font-medium">{item.itemName}</span>
                  <div className="flex items-center gap-3">
                    <span className={`font-mono font-bold ${out ? 'text-red-400' : 'text-amber-400'}`}>
                      {qty} {item.unit}
                    </span>
                    <span className={`text-xs font-mono font-bold px-2 py-0.5 rounded-tac ${out ? 'text-red-400 bg-tac-red/20 border border-tac-red/30' : 'text-amber-400 bg-tac-amber/20 border border-tac-amber/30'}`}>
                      {out ? 'OUT' : 'LOW'}
                    </span>
                  </div>
                </div>
              );
            })}
          </div>
        ) : (
          <p className="text-tac-green text-sm font-mono">[OK] All stock levels nominal.</p>
        )}
      </div>

      {/* Navigation tiles */}
      <div className="grid grid-cols-2 gap-4">
        {tiles.map(({ label, tag, path }) => (
          <button key={label} onClick={() => navigate(path)}
            className="tac-panel p-6 text-center hover:shadow-tac-glow hover:border-tac-green/40 transition active:scale-[0.98]">
            <div className="text-tac-green font-mono font-bold text-2xl mb-2 tracking-widest">[{tag}]</div>
            <div className="font-mono text-tac-text text-sm uppercase tracking-widest">{label}</div>
          </button>
        ))}
      </div>
    </div>
  );
}
