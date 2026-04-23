import React, { useState, useEffect, useCallback } from 'react';
import axios from 'axios';
import { useNavigate } from 'react-router-dom';
import Spinner from '../../components/Spinner';
import EmptyState from '../../components/EmptyState';
import { useToast } from '../../components/useToast';
import { ToastContainer } from '../../components/Toast';

// Format helpers
const fmt = new Intl.DateTimeFormat([], { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
const toISO = (d) => d.toISOString().split('T')[0];
const today = () => toISO(new Date());
const daysAgo = (n) => { const d = new Date(); d.setDate(d.getDate() - n); return toISO(d); };

export default function SalesReportPage() {
  const navigate = useNavigate();
  const { toasts, showToast, dismissToast } = useToast();

  const [from, setFrom] = useState(daysAgo(7));
  const [to, setTo] = useState(today());
  const [records, setRecords] = useState([]);
  const [totalRevenue, setTotalRevenue] = useState('0.00');
  const [count, setCount] = useState(0);
  const [peakHours, setPeakHours] = useState([]);
  const [menuBreakdown, setMenuBreakdown] = useState([]);
  const [loading, setLoading] = useState(true);

  const handleExport = async (format) => {
    try {
      const res = await axios.get('/api/sales/export', {
        params: { format, from, to },
        responseType: 'blob',
      });
      const mimeType = format === 'csv' ? 'text/csv' : 'application/pdf';
      const blob = new Blob([res.data], { type: mimeType });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `sales-report-${from}-to-${to}.${format}`;
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(url);
    } catch { showToast(`${format.toUpperCase()} export failed`, 'error'); }
  };

  const fetch = useCallback(async () => {
    setLoading(true);
    try {
      const res = await axios.get('/api/sales', { params: { from, to } });
      setRecords(res.data.records ?? []);
      setTotalRevenue(res.data.totalRevenue ?? '0.00');
      setCount(res.data.count ?? 0);
      setPeakHours(res.data.peakHours ?? []);
      setMenuBreakdown(res.data.menuBreakdown ?? []);
    } catch { showToast('Failed to load sales data', 'error'); }
    finally { setLoading(false); }
  }, [from, to, showToast]);

  useEffect(() => { fetch(); }, [fetch]);

  return (
    <div className="min-h-screen bg-tac-darker text-tac-text p-4 md:p-6">
      <ToastContainer toasts={toasts} onDismiss={dismissToast} />

      <header className="flex items-center justify-between mb-6 flex-wrap gap-3">
        <div>
          <button onClick={() => navigate('/manager')} className="text-tac-muted hover:text-tac-green text-xs font-mono mb-1 block uppercase tracking-widest">← Back to Command Center</button>
          <h1 className="text-2xl font-black tracking-tight font-mono text-tac-bright">Sales Report</h1>
        </div>
        <div className="flex items-center gap-2 flex-wrap">
          <input type="date" value={from} onChange={(e) => setFrom(e.target.value)}
            className="tac-input px-3 py-2 text-sm" />
          <span className="text-tac-muted font-mono text-xs">TO</span>
          <input type="date" value={to} onChange={(e) => setTo(e.target.value)}
            className="tac-input px-3 py-2 text-sm" />
          <button onClick={() => handleExport('csv')}
            className="min-h-[40px] px-4 tac-btn text-sm">
            CSV
          </button>
          <button onClick={() => handleExport('pdf')}
            className="min-h-[40px] px-4 tac-btn-danger text-sm">
            PDF
          </button>
        </div>
      </header>

      {/* Summary cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 mb-6">
        <div className="tac-panel p-6">
          <div className="text-tac-muted font-mono text-[10px] uppercase tracking-[0.2em] mb-2">REV</div>
          <div className="text-3xl font-black text-tac-green font-mono">${totalRevenue}</div>
          <div className="text-tac-muted text-sm mt-1">Total Revenue</div>
        </div>
        <div className="tac-panel p-6">
          <div className="text-tac-muted font-mono text-[10px] uppercase tracking-[0.2em] mb-2">ORD</div>
          <div className="text-3xl font-black text-tac-cyan font-mono">{count}</div>
          <div className="text-tac-muted text-sm mt-1">Orders Completed</div>
        </div>
      </div>

      {/* Peak Hours + Menu Breakdown */}
      {!loading && records.length > 0 && (
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 mb-6">
          <div className="tac-panel p-6">
            <h2 className="font-mono font-bold text-tac-green text-sm mb-3 uppercase tracking-widest">Peak Hours</h2>
            {peakHours.length > 0 ? (
              <div className="space-y-2">
                {peakHours.slice(0, 6).map((h) => (
                  <div key={h.hour} className="flex items-center justify-between text-sm">
                    <span className="text-tac-text font-mono">{h.label}</span>
                    <div className="flex items-center gap-4">
                      <span className="text-tac-muted font-mono">{h.orders} orders</span>
                      <span className="text-tac-green font-bold font-mono">${h.revenue}</span>
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <p className="text-tac-muted text-sm font-mono">No data for this period.</p>
            )}
          </div>

          <div className="tac-panel p-6">
            <h2 className="font-mono font-bold text-tac-green text-sm mb-3 uppercase tracking-widest">Revenue by Item</h2>
            {menuBreakdown.length > 0 ? (
              <div className="space-y-2">
                {menuBreakdown.slice(0, 8).map((item) => (
                  <div key={item.name} className="flex items-center justify-between text-sm">
                    <span className="text-tac-text">{item.name}</span>
                    <div className="flex items-center gap-4">
                      <span className="text-tac-muted font-mono">{item.quantity} sold</span>
                      <span className="text-tac-green font-bold font-mono">${item.revenue}</span>
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <p className="text-tac-muted text-sm font-mono">No data for this period.</p>
            )}
          </div>
        </div>
      )}

      {/* Records table */}
      {loading ? (
        <div className="flex justify-center py-20"><Spinner /></div>
      ) : records.length === 0 ? (
        <EmptyState message="NO SALES DATA FOR THIS PERIOD" />
      ) : (
        <div className="tac-panel overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-tac-border text-tac-muted text-[10px] font-mono uppercase tracking-[0.15em]">
                  <th className="text-left px-4 py-3">Order</th>
                  <th className="text-left px-4 py-3">Table</th>
                  <th className="text-left px-4 py-3">Items</th>
                  <th className="text-left px-4 py-3">Server</th>
                  <th className="text-right px-4 py-3">Amount</th>
                  <th className="text-left px-4 py-3">Completed</th>
                  <th className="text-left px-4 py-3">Payment</th>
                </tr>
              </thead>
              <tbody>
                {records.map((r) => {
                  const order = r.order;
                  const items = order?.orderItems ?? [];
                  return (
                    <tr key={r.id} className="border-b border-tac-border/50 hover:bg-tac-mid/30">
                      <td className="px-4 py-3 font-mono text-tac-muted">#{order?.id}</td>
                      <td className="px-4 py-3 font-bold text-tac-bright font-mono">{order?.tableNumber}</td>
                      <td className="px-4 py-3 text-tac-text">
                        {items.slice(0, 3).map((i) => `${i.quantity}x ${i.menuItem?.name ?? '?'}`).join(', ')}
                        {items.length > 3 && <span className="text-tac-muted"> +{items.length - 3} more</span>}
                      </td>
                      <td className="px-4 py-3 text-tac-muted">{order?.submittedBy?.name ?? '—'}</td>
                      <td className="px-4 py-3 text-right font-bold text-tac-green font-mono">${Number(r.totalAmount).toFixed(2)}</td>
                      <td className="px-4 py-3 text-tac-muted text-xs font-mono">{r.completedAt ? fmt.format(new Date(r.completedAt)) : '—'}</td>
                      <td className="px-4 py-3 text-tac-muted text-xs font-mono uppercase">
                        {r.paymentMethod}
                        {r.cardLast4 && (r.paymentMethod === 'CREDIT' || r.paymentMethod === 'DEBIT')
                          ? <span className="block text-tac-text normal-case">XXXX-XXXX-XXXX-{r.cardLast4}</span>
                          : null}
                        {r.cardLast4 && r.paymentMethod === 'GIFT_CARD'
                          ? <span className="block text-tac-text normal-case">****{r.cardLast4}</span>
                          : null}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>

          <div className="border-t border-tac-border px-4 py-3 flex justify-between text-sm font-mono">
            <span className="text-tac-muted">{count} orders in range</span>
            <span className="font-bold text-tac-green">Total: ${totalRevenue}</span>
          </div>
        </div>
      )}
    </div>
  );
}
