import React, { useState, useEffect, useCallback, useRef } from 'react';
import axios from 'axios';
import { useAuth } from '../../context/AuthContext';
import { getSocket } from '../../socket';
import RoleBadge from '../../components/RoleBadge';
import StatusBadge from '../../components/StatusBadge';
import Spinner from '../../components/Spinner';
import EmptyState from '../../components/EmptyState';
import { useToast } from '../../components/useToast';
import { ToastContainer } from '../../components/Toast';

// ── Live elapsed time display ────────────────────────────────────────────────
function TimeElapsed({ createdAt }) {
  const [label, setLabel] = useState('');

  useEffect(() => {
    const update = () => {
      const secs = Math.floor((Date.now() - new Date(createdAt).getTime()) / 1000);
      const m = Math.floor(secs / 60);
      const s = secs % 60;
      setLabel(m > 0 ? `${m}m ${s}s` : `${s}s`);
    };
    update();
    const id = setInterval(update, 1000);
    return () => clearInterval(id);
  }, [createdAt]);

  return <span className="text-tac-muted text-sm font-mono tabular-nums">{label}</span>;
}

// ── Inline delay-minutes input ───────────────────────────────────────────────
function DelayInput({ onConfirm, onCancel }) {
  const [minutes, setMinutes] = useState('');

  const submit = () => {
    const n = parseInt(minutes, 10);
    if (n > 0) onConfirm(n);
  };

  return (
    <div className="flex items-center gap-2 mt-2 flex-wrap">
      <input
        type="number"
        min="1"
        max="120"
        value={minutes}
        onChange={(e) => setMinutes(e.target.value)}
        onKeyDown={(e) => e.key === 'Enter' && submit()}
        placeholder="Min"
        className="w-20 tac-input px-3 py-2 text-sm"
      />
      <button
        onClick={submit}
        className="min-h-[40px] px-4 bg-tac-amber/20 hover:bg-tac-amber/30 text-amber-300 border border-tac-amber/30 text-sm rounded-tac font-mono font-bold transition"
      >
        SET
      </button>
      <button
        onClick={onCancel}
        className="min-h-[40px] px-4 tac-btn-muted text-sm"
      >
        CANCEL
      </button>
    </div>
  );
}

// ── Inline note input ────────────────────────────────────────────────────────
function NoteInput({ onConfirm, onCancel }) {
  const [text, setText] = useState('');

  const submit = () => {
    if (text.trim()) onConfirm(text.trim());
  };

  return (
    <div className="mt-2 w-full">
      <textarea
        value={text}
        onChange={(e) => setText(e.target.value.slice(0, 300))}
        onKeyDown={(e) => e.key === 'Enter' && !e.shiftKey && (e.preventDefault(), submit())}
        placeholder="Message to FOH staff..."
        rows={2}
        className="w-full tac-input px-3 py-2 text-sm resize-none"
      />
      <div className="flex gap-2 mt-1">
        <button
          onClick={submit}
          className="min-h-[36px] px-4 bg-tac-cyan/20 hover:bg-tac-cyan/30 text-cyan-300 border border-tac-cyan/30 text-sm rounded-tac font-mono font-bold transition"
        >
          SEND
        </button>
        <button
          onClick={onCancel}
          className="min-h-[36px] px-4 tac-btn-muted text-sm"
        >
          CANCEL
        </button>
      </div>
    </div>
  );
}

// ── History card (read-only) ─────────────────────────────────────────────────
function HistoryCard({ order }) {
  const items = order.items ?? order.orderItems ?? [];
  const BORDER = { COMPLETED: 'border-l-tac-green', CANCELLED: 'border-l-tac-red' };
  const TIME_FMT = new Intl.DateTimeFormat([], { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });

  const duration = (() => {
    const end = order.salesRecord?.completedAt;
    if (!end) return null;
    const secs = Math.max(0, Math.floor((new Date(end) - new Date(order.createdAt)) / 1000));
    const m = Math.floor(secs / 60);
    const s = secs % 60;
    return m > 0 ? `${m}m ${s}s` : `${s}s`;
  })();

  return (
    <div className={`tac-panel border-l-2 ${BORDER[order.status] ?? 'border-l-tac-border'} p-4`}>
      <div className="flex items-start justify-between mb-2">
        <div>
          <p className="text-xl font-black text-tac-bright leading-none font-mono">TBL {order.tableNumber}</p>
          <p className="text-tac-muted text-xs mt-0.5 font-mono">{TIME_FMT.format(new Date(order.createdAt))}</p>
        </div>
        <div className="flex flex-col items-end gap-1">
          <StatusBadge status={order.status} />
          {duration && (
            <span className="text-tac-muted text-xs font-mono">{duration} total</span>
          )}
        </div>
      </div>
      <ul className="space-y-0.5">
        {items.map((item, i) => (
          <li key={item.id ?? i} className="text-sm text-tac-text">
            <span className="font-bold text-tac-bright font-mono">{item.quantity}x</span>{' '}
            {item.menuItem?.name ?? item.name ?? 'Unknown item'}
            {item.specialInstructions && (
              <span className="ml-2 text-tac-muted italic">— {item.specialInstructions}</span>
            )}
          </li>
        ))}
      </ul>
      {order.bohNote && (
        <p className="mt-2 text-xs text-tac-cyan italic font-mono">NOTE: {order.bohNote}</p>
      )}
    </div>
  );
}

// ── Single order card ────────────────────────────────────────────────────────
function OrderCard({ order, onAccept, onDelay, onComplete, onDeny, onNote }) {
  const [showDelay, setShowDelay]   = useState(false);
  const [showNote,  setShowNote]    = useState(false);
  const [actionBusy, setActionBusy] = useState(null);

  const BORDER = {
    PENDING:     'border-l-tac-amber',
    IN_PROGRESS: 'border-l-tac-cyan',
    DELAYED:     'border-l-tac-red',
  };

  const wrap = async (key, fn) => {
    setActionBusy(key);
    await fn();
    setActionBusy(null);
  };

  const items = order.items ?? order.orderItems ?? [];
  const active = ['PENDING', 'IN_PROGRESS', 'DELAYED'].includes(order.status);

  return (
    <div className={`tac-panel border-l-2 ${BORDER[order.status] ?? 'border-l-tac-border'} p-4 shadow-tac animate-fade-in`}>
      {/* Header */}
      <div className="flex items-start justify-between mb-3">
        <div>
          <p className="text-3xl font-black text-tac-bright leading-none font-mono">TBL {order.tableNumber}</p>
          <div className="flex items-center gap-2 mt-1">
            <TimeElapsed createdAt={order.createdAt} />
            <StatusBadge status={order.status} />
          </div>
        </div>
        {order.status === 'DELAYED' && order.estimatedWait && (
          <span className="bg-tac-amber/15 text-amber-300 text-xs font-mono font-bold rounded-tac px-3 py-1.5 border border-tac-amber/30">
            ~{order.estimatedWait} MIN
          </span>
        )}
      </div>

      {/* Items */}
      <ul className="space-y-1 mb-3">
        {items.length === 0 && <li className="text-tac-muted text-sm italic font-mono">No items</li>}
        {items.map((item, i) => (
          <li key={item.id ?? i} className="text-sm text-tac-text">
            <span className="font-bold text-tac-bright font-mono">{item.quantity}x</span>{' '}
            {item.menuItem?.name ?? item.name ?? 'Unknown item'}
            {item.specialInstructions && (
              <span className="ml-2 text-tac-muted italic">— {item.specialInstructions}</span>
            )}
          </li>
        ))}
      </ul>

      {/* Existing BOH note */}
      {order.bohNote && (
        <p className="text-xs text-tac-cyan italic bg-tac-cyan/10 border border-tac-cyan/20 rounded-tac px-3 py-2 mb-3 font-mono">
          NOTE SENT: {order.bohNote}
        </p>
      )}

      {/* Actions */}
      <div className="flex flex-wrap gap-2">
        {order.status === 'PENDING' && (
          <button
            onClick={() => wrap('accept', () => onAccept(order.id))}
            disabled={!!actionBusy}
            className="min-h-[48px] flex-1 bg-tac-cyan/15 hover:bg-tac-cyan/25 border border-tac-cyan/30 disabled:opacity-50 text-cyan-300 font-mono font-bold rounded-tac transition uppercase tracking-wider text-sm"
          >
            {actionBusy === 'accept' ? <Spinner size="sm" /> : 'ACCEPT'}
          </button>
        )}

        {(order.status === 'IN_PROGRESS' || order.status === 'DELAYED') && (
          <button
            onClick={() => wrap('complete', () => onComplete(order.id))}
            disabled={!!actionBusy}
            className="min-h-[48px] flex-1 tac-btn disabled:opacity-50"
          >
            {actionBusy === 'complete' ? <Spinner size="sm" /> : 'COMPLETE'}
          </button>
        )}

        {(order.status === 'PENDING' || order.status === 'IN_PROGRESS') && !showDelay && (
          <button
            onClick={() => setShowDelay(true)}
            disabled={!!actionBusy}
            className="min-h-[48px] flex-1 bg-tac-amber/15 hover:bg-tac-amber/25 border border-tac-amber/30 disabled:opacity-50 text-amber-300 font-mono font-bold rounded-tac transition uppercase tracking-wider text-sm"
          >
            DELAY
          </button>
        )}

        {active && !showNote && (
          <button
            onClick={() => { setShowNote(true); setShowDelay(false); }}
            disabled={!!actionBusy}
            className="min-h-[48px] flex-1 tac-btn-muted disabled:opacity-50"
          >
            NOTE
          </button>
        )}

        {active && (
          <button
            onClick={() => wrap('deny', () => onDeny(order.id))}
            disabled={!!actionBusy}
            className="min-h-[48px] flex-1 tac-btn-danger disabled:opacity-50"
          >
            {actionBusy === 'deny' ? <Spinner size="sm" /> : 'DENY'}
          </button>
        )}

        {showDelay && (
          <DelayInput
            onConfirm={(mins) => wrap('delay', () => onDelay(order.id, mins)).then(() => setShowDelay(false))}
            onCancel={() => setShowDelay(false)}
          />
        )}

        {showNote && (
          <NoteInput
            onConfirm={(text) => wrap('note', () => onNote(order.id, text)).then(() => setShowNote(false))}
            onCancel={() => setShowNote(false)}
          />
        )}
      </div>
    </div>
  );
}

// ── Main dashboard ───────────────────────────────────────────────────────────
export default function BOHDashboard() {
  const { user, logout } = useAuth();
  const { toasts, showToast, dismissToast } = useToast();

  const [leftTab,     setLeftTab]     = useState('queue'); // 'queue' | 'history'
  const [orders,      setOrders]      = useState([]);
  const [ordersLoading, setOrdersLoading] = useState(true);
  const [history,     setHistory]     = useState([]);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [historyFetched, setHistoryFetched] = useState(false);
  const [menuItems,   setMenuItems]   = useState([]);
  const [menuLoading, setMenuLoading] = useState(true);
  const [alertText,   setAlertText]   = useState('');
  const [alertBusy,   setAlertBusy]   = useState(false);
  const [toggleBusy,  setToggleBusy]  = useState({});
  const [restockBusy, setRestockBusy] = useState({});

  const mounted = useRef(true);

  // Wait for socket to be ready (handles async connect race)
  const [socketReady, setSocketReady] = useState(!!getSocket());
  useEffect(() => {
    if (socketReady) return;
    const id = setInterval(() => { if (getSocket()) { setSocketReady(true); clearInterval(id); } }, 500);
    return () => clearInterval(id);
  }, [socketReady]);

  // ── Fetch active orders ──────────────────────────────────────────────────
  useEffect(() => {
    mounted.current = true;
    axios.get('/api/orders')
      .then((res) => {
        if (!mounted.current) return;
        const all = res.data.orders ?? res.data ?? [];
        setOrders(all.filter((o) => ['PENDING', 'IN_PROGRESS', 'DELAYED'].includes(o.status)));
      })
      .catch(() => { if (mounted.current) showToast('Failed to load orders', 'error'); })
      .finally(() => { if (mounted.current) setOrdersLoading(false); });
    return () => { mounted.current = false; };
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  // ── Fetch menu items ─────────────────────────────────────────────────────
  useEffect(() => {
    let active = true;
    axios.get('/api/menu')
      .then((res) => {
        if (!active) return;
        setMenuItems(res.data.items ?? res.data ?? []);
      })
      .catch(() => { if (active) showToast('Failed to load menu', 'error'); })
      .finally(() => { if (active) setMenuLoading(false); });
    return () => { active = false; };
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  // ── Fetch history (lazy — only when tab is opened) ───────────────────────
  const fetchHistory = useCallback(async () => {
    setHistoryLoading(true);
    try {
      const [comp, canc] = await Promise.all([
        axios.get('/api/orders?status=COMPLETED'),
        axios.get('/api/orders?status=CANCELLED'),
      ]);
      const all = [
        ...(comp.data.orders ?? comp.data ?? []),
        ...(canc.data.orders ?? canc.data ?? []),
      ].sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));
      setHistory(all);
      setHistoryFetched(true);
    } catch {
      showToast('Failed to load history', 'error');
    } finally {
      setHistoryLoading(false);
    }
  }, [showToast]);

  useEffect(() => {
    if (leftTab === 'history' && !historyFetched) fetchHistory();
  }, [leftTab, historyFetched, fetchHistory]);

  // ── Socket listeners ─────────────────────────────────────────────────────
  useEffect(() => {
    const socket = getSocket();
    if (!socket) return;

    const onNewOrder = (order) => {
      if (!mounted.current) return;
      setOrders((prev) => prev.find((o) => o.id === order.id) ? prev : [order, ...prev]);
    };

    const onStatusChanged = (data) => {
      if (!mounted.current) return;
      setOrders((prev) =>
        prev
          .map((o) => o.id === (data.orderId ?? data.id) ? { ...o, ...data } : o)
          .filter((o) => o.status !== 'COMPLETED')
      );
    };

    socket.on('order:new',           onNewOrder);
    socket.on('order:statusChanged', onStatusChanged);

    return () => {
      socket.off('order:new',           onNewOrder);
      socket.off('order:statusChanged', onStatusChanged);
    };
  }, [socketReady]); // eslint-disable-line react-hooks/exhaustive-deps

  // ── Polling fallback (when Socket.IO is unavailable, e.g. on Render) ──────
  useEffect(() => {
    const socket = getSocket();
    // If socket is connected, skip polling — real-time events handle it
    if (socket?.connected) return;

    const poll = () => {
      axios.get('/api/orders')
        .then((res) => {
          if (!mounted.current) return;
          const all = res.data.orders ?? res.data ?? [];
          setOrders(all.filter((o) => ['PENDING', 'IN_PROGRESS', 'DELAYED'].includes(o.status)));
        })
        .catch(() => {});
    };

    const id = setInterval(poll, 5000);
    return () => clearInterval(id);
  }, [socketReady]);

  // ── Sorted orders: PENDING → IN_PROGRESS → DELAYED, oldest first ─────────
  const sortedOrders = [...orders].sort((a, b) => {
    const P = { PENDING: 0, IN_PROGRESS: 1, DELAYED: 2 };
    const diff = (P[a.status] ?? 3) - (P[b.status] ?? 3);
    if (diff !== 0) return diff;
    return new Date(a.createdAt) - new Date(b.createdAt);
  });

  // ── Order action handlers ────────────────────────────────────────────────
  const handleAccept = useCallback(async (orderId) => {
    try {
      const res = await axios.patch(`/api/orders/${orderId}/status`, { status: 'IN_PROGRESS' });
      const updated = res.data.order ?? res.data;
      setOrders((prev) => prev.map((o) => o.id === orderId ? { ...o, ...updated } : o));
      getSocket()?.emit('order:acknowledged', { orderId });
    } catch {
      showToast('Failed to accept order', 'error');
    }
  }, [showToast]);

  const handleDelay = useCallback(async (orderId, estimatedWait) => {
    try {
      const res = await axios.patch(`/api/orders/${orderId}/status`, { status: 'DELAYED', estimatedWait });
      const updated = res.data.order ?? res.data;
      setOrders((prev) => prev.map((o) => o.id === orderId ? { ...o, ...updated } : o));
      getSocket()?.emit('order:delayed', { orderId, estimatedWait });
    } catch {
      showToast('Failed to set delay', 'error');
    }
  }, [showToast]);

  const handleComplete = useCallback(async (orderId) => {
    try {
      const res = await axios.patch(`/api/orders/${orderId}/status`, { status: 'COMPLETED' });
      const updated = res.data.order ?? res.data;
      setOrders((prev) => prev.filter((o) => o.id !== orderId));
      if (historyFetched) setHistory((prev) => [updated, ...prev]);
      showToast('Order completed', 'success');
    } catch {
      showToast('Failed to complete order', 'error');
    }
  }, [showToast, historyFetched]);

  const handleDeny = useCallback(async (orderId) => {
    try {
      const res = await axios.patch(`/api/orders/${orderId}/status`, { status: 'CANCELLED' });
      const updated = res.data.order ?? res.data;
      setOrders((prev) => prev.filter((o) => o.id !== orderId));
      if (historyFetched) setHistory((prev) => [updated, ...prev]);
      showToast('Order denied', 'success');
    } catch {
      showToast('Failed to deny order', 'error');
    }
  }, [showToast, historyFetched]);

  const handleNote = useCallback(async (orderId, note) => {
    try {
      const res = await axios.patch(`/api/orders/${orderId}/note`, { note });
      const updated = res.data;
      setOrders((prev) => prev.map((o) => o.id === orderId ? { ...o, bohNote: updated.bohNote } : o));
      showToast('Note sent to FOH', 'success');
    } catch {
      showToast('Failed to send note', 'error');
    }
  }, [showToast]);

  // ── Menu availability toggle ─────────────────────────────────────────────
  const handleToggle = useCallback(async (item) => {
    const isAvailable = item.availabilityStatus === 'AVAILABLE';
    const nextStatus  = isAvailable ? 'UNAVAILABLE' : 'AVAILABLE';
    setToggleBusy((prev) => ({ ...prev, [item.id]: true }));
    try {
      await axios.patch(`/api/menu/${item.id}/availability`, { availabilityStatus: nextStatus });
      setMenuItems((prev) => prev.map((m) => m.id === item.id ? { ...m, availabilityStatus: nextStatus } : m));
      getSocket()?.emit(nextStatus === 'AVAILABLE' ? 'item:available' : 'item:unavailable', { itemId: item.id, name: item.name });
    } catch {
      showToast('Failed to update item', 'error');
    } finally {
      setToggleBusy((prev) => ({ ...prev, [item.id]: false }));
    }
  }, [showToast]);

  // ── Restock ──────────────────────────────────────────────────────────────
  const handleRestock = useCallback(async (item) => {
    const invId = item.inventoryItem?.id;
    if (!invId) return;
    setRestockBusy((prev) => ({ ...prev, [invId]: true }));
    try {
      const res = await axios.post(`/api/inventory/${invId}/restock`);
      const updated = res.data;
      setMenuItems((prev) =>
        prev.map((m) =>
          m.inventoryItem?.id === invId
            ? { ...m, availabilityStatus: updated.wasEmpty ? 'AVAILABLE' : m.availabilityStatus, inventoryItem: { ...m.inventoryItem, quantity: updated.quantity } }
            : m
        )
      );
      showToast(`Restocked +${Number(updated.restocked)} ${item.inventoryItem.unit}`, 'success');
    } catch {
      showToast('Failed to restock', 'error');
    } finally {
      setRestockBusy((prev) => ({ ...prev, [invId]: false }));
    }
  }, [showToast]);

  // ── Alert broadcast ──────────────────────────────────────────────────────
  const handleBroadcast = useCallback(async () => {
    if (!alertText.trim()) return;
    setAlertBusy(true);
    try {
      await axios.post('/api/alerts', { message: alertText.trim(), recipientScope: 'BROADCAST' });
      showToast('Alert sent to all FOH staff', 'success');
      setAlertText('');
    } catch {
      showToast('Failed to send alert', 'error');
    } finally {
      setAlertBusy(false);
    }
  }, [alertText, showToast]);

  // ── Inventory live updates ───────────────────────────────────────────────
  useEffect(() => {
    const socket = getSocket();
    if (!socket) return;
    const onInventoryUpdated = (updatedItems) => {
      setMenuItems((prev) =>
        prev.map((m) => {
          if (!m.inventoryItem) return m;
          const updated = updatedItems.find((u) => u.id === m.inventoryItem.id);
          if (!updated) return m;
          return { ...m, inventoryItem: { ...m.inventoryItem, quantity: updated.quantity } };
        })
      );
    };
    const onItemUnavailable = ({ menuItemId }) => {
      setMenuItems((prev) =>
        prev.map((m) => m.id === menuItemId ? { ...m, availabilityStatus: 'UNAVAILABLE' } : m)
      );
    };

    const onItemAvailable = ({ menuItemId }) => {
      setMenuItems((prev) =>
        prev.map((m) => m.id === menuItemId ? { ...m, availabilityStatus: 'AVAILABLE' } : m)
      );
    };

    // Menu CRUD events (manager adds/edits/removes menu items in real time)
    const onMenuCreated = (item) => {
      setMenuItems((prev) => prev.find((m) => m.id === item.id) ? prev : [...prev, item]);
    };
    const onMenuUpdated = (item) => {
      setMenuItems((prev) => prev.map((m) => m.id === item.id ? { ...m, ...item } : m));
    };
    const onMenuDeleted = ({ id }) => {
      setMenuItems((prev) => prev.filter((m) => m.id !== id));
    };

    socket.on('inventory:updated', onInventoryUpdated);
    socket.on('item:unavailable', onItemUnavailable);
    socket.on('item:available',   onItemAvailable);
    socket.on('menu:created',     onMenuCreated);
    socket.on('menu:updated',     onMenuUpdated);
    socket.on('menu:deleted',     onMenuDeleted);
    return () => {
      socket.off('inventory:updated', onInventoryUpdated);
      socket.off('item:unavailable',  onItemUnavailable);
      socket.off('item:available',    onItemAvailable);
      socket.off('menu:created',      onMenuCreated);
      socket.off('menu:updated',      onMenuUpdated);
      socket.off('menu:deleted',      onMenuDeleted);
    };
  }, [socketReady]); // eslint-disable-line react-hooks-exhaustive-deps

  // ── Group menu by category ───────────────────────────────────────────────
  // Hide orphaned / unavailable items that have no inventory link
  const visibleMenu = menuItems.filter(
    (item) => item.availabilityStatus !== 'UNAVAILABLE' || item.inventoryItemId != null
  );
  const menuByCategory = visibleMenu.reduce((acc, item) => {
    const cat = item.category ?? 'Other';
    (acc[cat] ??= []).push(item);
    return acc;
  }, {});

  // ────────────────────────────────────────────────────────────────────────
  return (
    <div className="min-h-screen bg-tac-darker flex flex-col">
      <ToastContainer toasts={toasts} onDismiss={dismissToast} />

      {/* ── Status Bar / Navbar ── */}
      <nav className="bg-tac-black border-b border-tac-border px-4 py-3 flex items-center justify-between sticky top-0 z-10">
        <div className="flex items-center gap-3">
          <span className="text-tac-green font-mono font-black text-xl tracking-widest">SOS</span>
          <span className="text-tac-border">|</span>
          <span className="text-tac-muted font-mono text-xs uppercase tracking-widest hidden sm:block">Kitchen Ops</span>
        </div>
        <div className="flex items-center gap-3">
          <span className="text-tac-text text-sm font-mono hidden sm:block">{user?.name}</span>
          <RoleBadge role={user?.role} />
          <button
            onClick={logout}
            className="min-h-[48px] min-w-[48px] px-4 tac-btn-danger"
          >
            LOGOUT
          </button>
        </div>
      </nav>

      {/* ── Two-panel layout ── */}
      <div className="flex-1 flex overflow-hidden">

        {/* LEFT: Live Order Queue + History (60%) */}
        <section className="w-3/5 flex flex-col border-r border-tac-border min-w-0">
          {/* Tab header */}
          <div className="px-4 border-b border-tac-border flex items-center gap-1 pt-1 bg-tac-black/50">
            <button
              onClick={() => setLeftTab('queue')}
              className={`flex items-center gap-2 px-4 py-2.5 text-xs font-mono font-bold uppercase tracking-widest transition border-b-2 ${
                leftTab === 'queue'
                  ? 'text-tac-green border-tac-green'
                  : 'text-tac-muted border-transparent hover:text-tac-text'
              }`}
            >
              Live Queue
              {orders.length > 0 && (
                <span className="bg-tac-red/20 text-red-400 border border-tac-red/30 text-xs font-bold rounded-tac px-2 py-0.5">
                  {orders.length}
                </span>
              )}
            </button>
            <button
              onClick={() => setLeftTab('history')}
              className={`flex items-center gap-2 px-4 py-2.5 text-xs font-mono font-bold uppercase tracking-widest transition border-b-2 ${
                leftTab === 'history'
                  ? 'text-tac-green border-tac-green'
                  : 'text-tac-muted border-transparent hover:text-tac-text'
              }`}
            >
              History
              {historyFetched && history.length > 0 && (
                <span className="bg-tac-mid text-tac-muted border border-tac-border text-xs font-bold rounded-tac px-2 py-0.5">
                  {history.length}
                </span>
              )}
            </button>
          </div>

          {/* Queue tab */}
          {leftTab === 'queue' && (
            <div className="flex-1 overflow-y-auto p-4 space-y-3">
              {ordersLoading ? (
                <div className="flex items-center justify-center h-40"><Spinner /></div>
              ) : sortedOrders.length === 0 ? (
                <EmptyState message="NO ACTIVE ORDERS — STANDING BY" />
              ) : (
                sortedOrders.map((order) => (
                  <OrderCard
                    key={order.id}
                    order={order}
                    onAccept={handleAccept}
                    onDelay={handleDelay}
                    onComplete={handleComplete}
                    onDeny={handleDeny}
                    onNote={handleNote}
                  />
                ))
              )}
            </div>
          )}

          {/* History tab */}
          {leftTab === 'history' && (
            <div className="flex-1 overflow-y-auto p-4 space-y-3">
              {historyLoading ? (
                <div className="flex items-center justify-center h-40"><Spinner /></div>
              ) : history.length === 0 ? (
                <EmptyState message="NO COMPLETED OR CANCELLED ORDERS YET" />
              ) : (
                history.map((order) => <HistoryCard key={order.id} order={order} />)
              )}
            </div>
          )}
        </section>

        {/* RIGHT: Quick Actions (40%) */}
        <section className="w-2/5 flex flex-col overflow-y-auto min-w-0">

          {/* Item Availability */}
          <div className="p-4 border-b border-tac-border">
            <h2 className="text-tac-green font-mono font-bold text-sm mb-4 uppercase tracking-widest">Item Availability</h2>
            {menuLoading ? (
              <Spinner />
            ) : Object.keys(menuByCategory).length === 0 ? (
              <EmptyState message="No menu items found." />
            ) : (
              Object.entries(menuByCategory).map(([category, items]) => (
                <div key={category} className="mb-5">
                  <p className="text-tac-muted text-[10px] font-mono font-bold uppercase tracking-[0.2em] mb-2">
                    {category}
                  </p>
                  <div className="space-y-2">
                    {items.map((item) => {
                      const inv = item.inventoryItem;
                      const qty = inv ? Number(inv.quantity) : null;
                      const lowStock = inv && qty <= Number(inv.lowStockThreshold);
                      const outOfStock = inv && qty <= 0;
                      return (
                        <div
                          key={item.id}
                          className={`flex items-center justify-between rounded-tac px-3 py-2 gap-2 ${outOfStock ? 'bg-tac-red/10 border border-tac-red/20' : lowStock ? 'bg-tac-amber/10 border border-tac-amber/20' : 'bg-tac-dark border border-tac-border'}`}
                        >
                          <div className="min-w-0 flex-1">
                            <span className="text-tac-text text-sm font-medium block truncate">{item.name}</span>
                            {inv && (
                              <span className={`text-xs font-mono ${outOfStock ? 'text-red-400 font-bold' : lowStock ? 'text-amber-400 font-semibold' : 'text-tac-muted'}`}>
                                {outOfStock ? 'OUT OF STOCK' : `${qty} in stock${lowStock ? ' — LOW' : ''}`}
                              </span>
                            )}
                          </div>
                          <div className="flex items-center gap-1.5 flex-shrink-0">
                            {inv && (
                              <button
                                onClick={() => handleRestock(item)}
                                disabled={!!restockBusy[inv.id]}
                                title={`Restock +${Number(inv.restockAmount)} ${inv.unit}`}
                                className="min-h-[48px] px-3 rounded-tac text-xs font-mono font-bold bg-tac-mid hover:bg-tac-border text-tac-text transition disabled:opacity-50 border border-tac-border"
                              >
                                {restockBusy[inv.id] ? '...' : `+${Number(inv.restockAmount)}`}
                              </button>
                            )}
                            <button
                              onClick={() => handleToggle(item)}
                              disabled={!!toggleBusy[item.id]}
                              className={`min-h-[48px] min-w-[88px] rounded-tac text-xs font-mono font-bold transition ${
                                item.availabilityStatus === 'AVAILABLE'
                                  ? 'bg-tac-green/15 hover:bg-tac-green/25 text-tac-lime border border-tac-green/30'
                                  : 'bg-tac-red/15 hover:bg-tac-red/25 text-red-400 border border-tac-red/30'
                              } disabled:opacity-50`}
                            >
                              {toggleBusy[item.id] ? '...' : item.availabilityStatus === 'AVAILABLE' ? 'ONLINE' : 'OFFLINE'}
                            </button>
                          </div>
                        </div>
                      );
                    })}
                  </div>
                </div>
              ))
            )}
          </div>

          {/* Alert Broadcaster (UC-04) */}
          <div className="p-4">
            <h2 className="text-tac-green font-mono font-bold text-sm mb-3 uppercase tracking-widest">Broadcast Alert</h2>
            {/* Quick Templates (UC-04 Ext 3a) */}
            <div className="flex flex-wrap gap-1.5 mb-3">
              {['86\'d — item sold out', 'Rush incoming — all hands', 'Equipment issue — stand by', 'Menu change — check board', 'VIP table — priority service'].map((tpl) => (
                <button key={tpl} onClick={() => setAlertText(tpl)}
                  className="px-2.5 py-1 rounded-tac text-xs font-mono bg-tac-mid hover:bg-tac-border text-tac-text border border-tac-border transition">
                  {tpl}
                </button>
              ))}
            </div>
            <div className="relative">
              <textarea
                value={alertText}
                onChange={(e) => setAlertText(e.target.value.slice(0, 200))}
                placeholder="Type a message for all FOH staff..."
                rows={3}
                className="w-full tac-input p-3 text-sm resize-none"
              />
              <span className={`absolute bottom-3 right-3 text-xs pointer-events-none font-mono ${alertText.length > 180 ? 'text-red-400' : 'text-tac-muted'}`}>
                {alertText.length}/200
              </span>
            </div>
            {!alertText.trim() && <p className="text-tac-muted text-xs mt-1 font-mono">Select a template or type a custom message.</p>}
            <button
              onClick={handleBroadcast}
              disabled={alertBusy || !alertText.trim()}
              className="mt-3 w-full min-h-[52px] tac-btn-danger disabled:opacity-50"
            >
              {alertBusy ? <Spinner size="sm" /> : 'BROADCAST TO ALL FOH'}
            </button>
          </div>

        </section>
      </div>
    </div>
  );
}
