import React, { useState, useEffect, useCallback } from 'react';
import axios from 'axios';
import { useNavigate } from 'react-router-dom';
import Spinner from '../../components/Spinner';
import EmptyState from '../../components/EmptyState';
import { useToast } from '../../components/useToast';
import { ToastContainer } from '../../components/Toast';

const PAGE_SIZE = 50;
const fmt = new Intl.DateTimeFormat([], { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit', second: '2-digit' });

// Color mapping for event types
const EVENT_COLORS = {
  AUTH_MISSING_TOKEN: 'text-red-400',
  AUTH_INVALID_TOKEN: 'text-red-400',
  LOGIN_FAILED: 'text-red-400',
  LOGIN_SUCCESS: 'text-tac-green',
  ORDER_CANCELLED: 'text-amber-400',
  USER_CREATED: 'text-tac-cyan',
  USER_DELETED: 'text-red-400',
  PERMISSION_CHANGE: 'text-amber-400',
  PASSWORD_RESET: 'text-amber-400',
  INVENTORY_CREATE: 'text-tac-cyan',
  INVENTORY_UPDATE: 'text-tac-cyan',
  INVENTORY_DELETE: 'text-red-400',
  INVENTORY_RESTOCK: 'text-tac-green',
  MENU_CREATE: 'text-tac-cyan',
  MENU_UPDATE: 'text-tac-cyan',
  MENU_PRICE_CHANGE: 'text-amber-400',
  MENU_DELETE: 'text-red-400',
};

export default function SecurityLogsPage() {
  const navigate = useNavigate();
  const { toasts, showToast, dismissToast } = useToast();

  const [logs, setLogs] = useState([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [eventFilter, setEventFilter] = useState('');
  const [eventTypes, setEventTypes] = useState([]);

  const fetchLogs = useCallback(async () => {
    setLoading(true);
    try {
      const res = await axios.get('/api/users/security-logs', {
        params: { limit: PAGE_SIZE, offset: page * PAGE_SIZE },
      });
      setLogs(res.data.logs ?? []);
      setTotal(res.data.total ?? 0);

      if (page === 0 && eventTypes.length === 0) {
        const types = [...new Set((res.data.logs ?? []).map((l) => l.event))].sort();
        setEventTypes(types);
      }
    } catch { showToast('Failed to load security logs', 'error'); }
    finally { setLoading(false); }
  }, [page, showToast]); // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => { fetchLogs(); }, [fetchLogs]);

  const filteredLogs = eventFilter ? logs.filter((l) => l.event === eventFilter) : logs;
  const totalPages = Math.ceil(total / PAGE_SIZE);

  return (
    <div className="min-h-screen bg-tac-darker text-tac-text p-4 md:p-6">
      <ToastContainer toasts={toasts} onDismiss={dismissToast} />

      <header className="flex items-center justify-between mb-6 flex-wrap gap-3">
        <div>
          <button onClick={() => navigate('/manager')} className="text-tac-muted hover:text-tac-green text-xs font-mono mb-1 block uppercase tracking-widest">← Back to Command Center</button>
          <h1 className="text-2xl font-black tracking-tight font-mono text-tac-bright">Security Logs</h1>
          <p className="text-tac-muted text-xs font-mono mt-0.5">{total} total events</p>
        </div>
        <div className="flex items-center gap-2">
          <select value={eventFilter} onChange={(e) => setEventFilter(e.target.value)}
            className="tac-input px-3 py-2 text-sm">
            <option value="">All Events</option>
            {eventTypes.map((t) => <option key={t} value={t}>{t}</option>)}
          </select>
        </div>
      </header>

      {loading ? (
        <div className="flex justify-center py-20"><Spinner /></div>
      ) : filteredLogs.length === 0 ? (
        <EmptyState message="NO SECURITY LOG ENTRIES FOUND" />
      ) : (
        <>
          <div className="tac-panel overflow-hidden mb-4">
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-tac-border text-tac-muted text-[10px] font-mono uppercase tracking-[0.15em]">
                    <th className="text-left px-4 py-3">Timestamp</th>
                    <th className="text-left px-4 py-3">Event</th>
                    <th className="text-left px-4 py-3">User</th>
                    <th className="text-left px-4 py-3">Details</th>
                    <th className="text-left px-4 py-3">IP</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredLogs.map((log) => (
                    <tr key={log.id} className="border-b border-tac-border/50 hover:bg-tac-mid/30">
                      <td className="px-4 py-3 text-tac-muted text-xs whitespace-nowrap font-mono">
                        {fmt.format(new Date(log.timestamp))}
                      </td>
                      <td className={`px-4 py-3 font-bold text-xs whitespace-nowrap font-mono ${EVENT_COLORS[log.event] ?? 'text-tac-text'}`}>
                        {log.event}
                      </td>
                      <td className="px-4 py-3 text-tac-text">
                        {log.user ? `${log.user.name} (${log.user.username})` : '—'}
                      </td>
                      <td className="px-4 py-3 text-tac-muted text-xs max-w-xs truncate font-mono" title={log.details}>
                        {log.details || '—'}
                      </td>
                      <td className="px-4 py-3 text-tac-muted text-xs font-mono">{log.ipAddress || '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>

          {/* Pagination */}
          {totalPages > 1 && (
            <div className="flex items-center justify-center gap-2">
              <button onClick={() => setPage(Math.max(0, page - 1))} disabled={page === 0}
                className="min-h-[40px] px-4 tac-btn-muted disabled:opacity-40">
                ← PREV
              </button>
              <span className="text-tac-muted text-xs font-mono">PAGE {page + 1} OF {totalPages}</span>
              <button onClick={() => setPage(Math.min(totalPages - 1, page + 1))} disabled={page >= totalPages - 1}
                className="min-h-[40px] px-4 tac-btn-muted disabled:opacity-40">
                NEXT →
              </button>
            </div>
          )}
        </>
      )}
    </div>
  );
}
