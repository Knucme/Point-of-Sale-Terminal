import React, { useEffect, useState, useCallback, useRef } from 'react';
import axios from 'axios';
import { useAuth } from '../../context/AuthContext';
import { getSocket, recordAlertTimestamp } from '../../socket';
import StatusBadge from '../../components/StatusBadge';
import Spinner from '../../components/Spinner';
import EmptyState from '../../components/EmptyState';
import { useToast } from '../../components/useToast';
import { ToastContainer } from '../../components/Toast';
import tableImage from '../../assets/dining-table.png';

// ── Category icons ──────────────────────────────────────────────────────────
const CAT_ICONS = {
  Mains: '🥩', Sides: '🍟', Appetizers: '🧀', Drinks: '🍺', Desserts: '🍫', Other: '📦',
};

// ── Live elapsed time ───────────────────────────────────────────────────────
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
  return <span className="text-tac-muted text-xs font-mono tabular-nums">{label}</span>;
}

// ── Item detail modal ───────────────────────────────────────────────────────
function ItemModal({ item, onAdd, onClose }) {
  const [qty, setQty] = useState(1);
  const [instructions, setInstructions] = useState('');

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4">
      <div className="tac-panel shadow-tac-glow max-w-sm w-full p-6 animate-fade-in">
        <h2 className="text-lg font-bold text-tac-bright font-mono mb-1">{item.name}</h2>
        <p className="text-tac-green font-bold text-xl font-mono mb-4">${Number(item.price).toFixed(2)}</p>

        <label className="text-tac-muted text-xs font-mono uppercase tracking-widest block mb-1">Quantity</label>
        <div className="flex items-center gap-3 mb-4">
          <button onClick={() => setQty(Math.max(1, qty - 1))}
            className="min-h-[48px] min-w-[48px] bg-tac-mid hover:bg-tac-border text-tac-text text-xl font-bold rounded-tac transition border border-tac-border">−</button>
          <span className="text-tac-bright text-2xl font-black font-mono w-8 text-center">{qty}</span>
          <button onClick={() => setQty(Math.min(20, qty + 1))}
            className="min-h-[48px] min-w-[48px] bg-tac-mid hover:bg-tac-border text-tac-text text-xl font-bold rounded-tac transition border border-tac-border">+</button>
        </div>

        <label className="text-tac-muted text-xs font-mono uppercase tracking-widest block mb-1">Special Instructions</label>
        <textarea value={instructions} onChange={(e) => setInstructions(e.target.value.slice(0, 200))}
          placeholder="No onions, extra sauce..." rows={2}
          className="w-full tac-input px-3 py-2 text-sm resize-none mb-4" />

        <div className="flex gap-3">
          <button onClick={onClose}
            className="flex-1 min-h-[48px] tac-btn-muted">
            CANCEL
          </button>
          <button onClick={() => { onAdd({ menuItemId: item.id, name: item.name, price: item.price, quantity: qty, specialInstructions: instructions.trim() || null }); onClose(); }}
            className="flex-1 min-h-[48px] tac-btn">
            ADD TO ORDER
          </button>
        </div>
      </div>
    </div>
  );
}

// ── Payment method selector modal ───────────────────────────────────────────
function PaymentModal({ tableNum, total, onConfirm, onClose }) {
  const [method, setMethod] = useState('');
  const [cardNumber, setCardNumber] = useState('');
  const [expDate, setExpDate] = useState('');
  const [cvv, setCvv] = useState('');
  const [giftCardNumber, setGiftCardNumber] = useState('');
  const methods = ['CASH', 'CREDIT', 'DEBIT', 'GIFT_CARD'];

  const isCard = method === 'CREDIT' || method === 'DEBIT';
  const isGift = method === 'GIFT_CARD';

  // Validate card fields
  const cardDigitsOnly = cardNumber.replace(/\D/g, '');
  const cvvDigitsOnly = cvv.replace(/\D/g, '');

  // Check if card is expired
  const isExpired = (() => {
    if (!isCard || !/^\d{2}\/\d{2}$/.test(expDate)) return false;
    const [mm, yy] = expDate.split('/').map(Number);
    if (mm < 1 || mm > 12) return true;
    const expMonth = mm;
    const expYear = 2000 + yy;
    const now = new Date();
    const currentMonth = now.getMonth() + 1;
    const currentYear = now.getFullYear();
    return expYear < currentYear || (expYear === currentYear && expMonth < currentMonth);
  })();

  const cardValid = isCard
    ? (cardDigitsOnly.length === 16 && /^\d{2}\/\d{2}$/.test(expDate) && !isExpired && cvvDigitsOnly.length === 3)
    : true;
  const giftValid = isGift ? giftCardNumber.trim().length >= 4 : true;
  const canConfirm = method && cardValid && giftValid;

  // Format card number with spaces as user types (16 digits max)
  const formatCardNumber = (val) => {
    const digits = val.replace(/\D/g, '').slice(0, 16);
    return digits.replace(/(.{4})/g, '$1 ').trim();
  };

  // Format exp date as MM/YY
  const formatExpDate = (val) => {
    const digits = val.replace(/\D/g, '').slice(0, 4);
    if (digits.length > 2) return digits.slice(0, 2) + '/' + digits.slice(2);
    return digits;
  };

  const handleConfirm = () => {
    let cardLast4 = null;
    if (isCard) {
      cardLast4 = cardDigitsOnly.slice(-4);
    } else if (isGift) {
      cardLast4 = giftCardNumber.trim().slice(-4);
    }
    onConfirm(method, cardLast4);
  };

  return (
    <div className="fixed inset-0 z-[60] flex items-center justify-center bg-black/80 p-4">
      <div className="tac-panel shadow-tac-glow max-w-sm w-full p-6 animate-fade-in">
        <h2 className="text-lg font-bold text-tac-bright font-mono mb-1">CHECKOUT — TBL {tableNum}</h2>
        <p className="text-tac-green font-bold text-2xl font-mono mb-5">${total.toFixed(2)}</p>

        <label className="text-tac-muted text-xs font-mono uppercase tracking-widest block mb-2">Payment Method</label>
        <div className="grid grid-cols-2 gap-2 mb-4">
          {methods.map((m) => (
            <button key={m} onClick={() => { setMethod(m); setCardNumber(''); setExpDate(''); setCvv(''); setGiftCardNumber(''); }}
              className={`min-h-[48px] rounded-tac font-mono font-bold text-sm uppercase tracking-wider transition border ${method === m
                ? 'bg-tac-green/20 border-tac-green/50 text-tac-lime'
                : 'bg-tac-mid border-tac-border text-tac-text hover:border-tac-green/30'}`}>
              {m.replace('_', ' ')}
            </button>
          ))}
        </div>

        {/* Card input for CREDIT / DEBIT */}
        {isCard && (
          <div className="space-y-3 mb-4 animate-fade-in">
            <div>
              <label className="text-tac-muted text-xs font-mono uppercase tracking-widest block mb-1">Card Number</label>
              <input type="text" inputMode="numeric" value={cardNumber}
                onChange={(e) => setCardNumber(formatCardNumber(e.target.value))}
                placeholder="0000 0000 0000 0000"
                maxLength={19}
                className="w-full tac-input px-3 py-2.5 text-sm font-mono tracking-wider" />
              {cardDigitsOnly.length > 0 && cardDigitsOnly.length < 16 && (
                <p className="text-tac-amber text-xs font-mono mt-1">{16 - cardDigitsOnly.length} digits remaining</p>
              )}
            </div>
            <div className="flex gap-3">
              <div className="flex-1">
                <label className="text-tac-muted text-xs font-mono uppercase tracking-widest block mb-1">Exp Date</label>
                <input type="text" inputMode="numeric" value={expDate}
                  onChange={(e) => setExpDate(formatExpDate(e.target.value))}
                  placeholder="MM/YY"
                  maxLength={5}
                  className={`w-full tac-input px-3 py-2.5 text-sm font-mono tracking-wider ${isExpired ? 'border-red-500' : ''}`} />
                {isExpired && <p className="text-red-400 text-xs font-mono mt-1">Card expired</p>}
              </div>
              <div className="w-24">
                <label className="text-tac-muted text-xs font-mono uppercase tracking-widest block mb-1">CVV</label>
                <input type="text" inputMode="numeric" value={cvv}
                  onChange={(e) => setCvv(e.target.value.replace(/\D/g, '').slice(0, 3))}
                  placeholder="000"
                  maxLength={3}
                  className="w-full tac-input px-3 py-2.5 text-sm font-mono tracking-wider" />
              </div>
            </div>
          </div>
        )}

        {/* Gift card input */}
        {isGift && (
          <div className="mb-4 animate-fade-in">
            <label className="text-tac-muted text-xs font-mono uppercase tracking-widest block mb-1">Gift Card Number</label>
            <input type="text" value={giftCardNumber}
              onChange={(e) => setGiftCardNumber(e.target.value.slice(0, 30))}
              placeholder="Enter gift card number"
              className="w-full tac-input px-3 py-2.5 text-sm font-mono tracking-wider" />
          </div>
        )}

        <div className="flex gap-3">
          <button onClick={onClose}
            className="flex-1 min-h-[48px] tac-btn-muted">CANCEL</button>
          <button onClick={handleConfirm}
            disabled={!canConfirm}
            className="flex-1 min-h-[48px] bg-tac-green/15 hover:bg-tac-green/25 border border-tac-green/30 text-tac-lime font-mono font-bold rounded-tac transition text-sm uppercase tracking-wider disabled:opacity-40 disabled:cursor-not-allowed">
            CONFIRM
          </button>
        </div>
      </div>
    </div>
  );
}

// ── Table detail modal ──────────────────────────────────────────────────────
function TableModal({ tableNum, orders, onClose, onAddFood, onCheckout, onPrintReceipt }) {
  const allItems = orders.flatMap((o) => (o.orderItems ?? []).map((item) => ({ ...item, orderId: o.id, orderStatus: o.status })));
  const total = allItems.reduce((sum, item) => sum + (item.menuItem?.price ? Number(item.menuItem.price) * item.quantity : 0), 0);
  const hasActiveOrders = orders.some((o) => ['PENDING', 'IN_PROGRESS', 'DELAYED'].includes(o.status));
  const [showPayment, setShowPayment] = useState(false);

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4">
      {showPayment && (
        <PaymentModal
          tableNum={tableNum}
          total={total}
          onClose={() => setShowPayment(false)}
          onConfirm={(method, cardLast4) => { setShowPayment(false); onCheckout(tableNum, method, cardLast4); }}
        />
      )}
      <div className="tac-panel shadow-tac-glow max-w-lg w-full max-h-[85vh] flex flex-col animate-fade-in">
        {/* Header */}
        <div className="px-6 pt-5 pb-3 border-b border-tac-border flex items-center justify-between">
          <div className="flex items-center gap-3">
            <img src={tableImage} alt="Table" className="w-10 h-10 opacity-70" />
            <h2 className="text-xl font-black text-tac-bright font-mono">TBL {tableNum}</h2>
          </div>
          <button onClick={onClose} className="text-tac-muted hover:text-tac-text text-2xl leading-none transition">✕</button>
        </div>

        {/* Orders list */}
        <div className="flex-1 overflow-y-auto px-6 py-4">
          {orders.length === 0 ? (
            <EmptyState message="NO ORDERS FOR THIS TABLE" />
          ) : (
            <div className="space-y-4">
              {orders.map((order) => {
                const items = order.orderItems ?? [];
                const orderTotal = items.reduce((s, i) => s + (i.menuItem?.price ? Number(i.menuItem.price) * i.quantity : 0), 0);
                return (
                  <div key={order.id} className="bg-tac-mid/50 rounded-tac p-3 border border-tac-border">
                    <div className="flex items-center justify-between mb-2">
                      <span className="text-tac-muted text-xs font-mono">ORD #{order.id}</span>
                      <StatusBadge status={order.status} />
                    </div>
                    {order.bohNote && (
                      <p className="text-tac-cyan text-sm italic bg-tac-cyan/10 border border-tac-cyan/20 rounded-tac px-3 py-2 mb-2 font-mono">Kitchen: {order.bohNote}</p>
                    )}
                    <ul className="space-y-1">
                      {items.map((item, i) => (
                        <li key={item.id ?? i} className="text-sm text-tac-text flex justify-between">
                          <span>
                            <span className="font-bold text-tac-bright font-mono">{item.quantity}x</span>{' '}
                            {item.menuItem?.name ?? 'Unknown'}
                            {item.specialInstructions && <span className="text-tac-muted italic ml-1">— {item.specialInstructions}</span>}
                          </span>
                          <span className="text-tac-muted ml-2 font-mono">${(Number(item.menuItem?.price ?? 0) * item.quantity).toFixed(2)}</span>
                        </li>
                      ))}
                    </ul>
                    <div className="text-right text-sm text-tac-muted mt-1 pt-1 border-t border-tac-border font-mono">
                      Subtotal: ${orderTotal.toFixed(2)}
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="border-t border-tac-border px-6 py-4">
          <div className="flex justify-between text-tac-bright font-bold text-lg mb-4 font-mono">
            <span>TOTAL</span>
            <span>${total.toFixed(2)}</span>
          </div>
          <div className="flex gap-2">
            <button onClick={() => onAddFood(tableNum)}
              className="flex-1 min-h-[48px] tac-btn text-sm">
              + ADD FOOD
            </button>
            <button onClick={() => onPrintReceipt(tableNum, orders, total)}
              className="flex-1 min-h-[48px] tac-btn-muted text-sm">
              RECEIPT
            </button>
            <button onClick={() => setShowPayment(true)}
              disabled={!hasActiveOrders}
              className="flex-1 min-h-[48px] bg-tac-green/15 hover:bg-tac-green/25 border border-tac-green/30 text-tac-lime font-mono font-bold rounded-tac transition text-sm uppercase tracking-wider disabled:opacity-40 disabled:cursor-not-allowed">
              CHECKOUT
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

// ── Past orders grouped by table → then by receipt ─────────────────────────
function PastOrdersByTable({ pastOrders, onPrintReceipt }) {
  const [expandedTable, setExpandedTable] = useState(null);
  const [expandedReceipt, setExpandedReceipt] = useState(null);

  // Group past orders: table → receipt number → orders
  const tableGroups = React.useMemo(() => {
    const tableMap = {};
    pastOrders.forEach((order) => {
      const tbl = order.tableNumber;
      if (!tableMap[tbl]) tableMap[tbl] = [];
      tableMap[tbl].push(order);
    });

    return Object.entries(tableMap)
      .map(([tbl, orders]) => {
        // Group by receipt number within this table
        const receiptMap = {};
        orders.forEach((order) => {
          const rn = order.receiptNumber || `legacy-${order.id}`;
          if (!receiptMap[rn]) receiptMap[rn] = [];
          receiptMap[rn].push(order);
        });

        const receipts = Object.entries(receiptMap)
          .map(([rn, rOrders]) => ({
            receiptNumber: rn,
            isLegacy: rn.startsWith('legacy-'),
            orders: rOrders.sort((a, b) => new Date(a.createdAt) - new Date(b.createdAt)),
            total: rOrders.reduce((sum, o) =>
              sum + (o.orderItems ?? []).reduce((s, i) => s + (Number(i.menuItem?.price ?? 0) * i.quantity), 0), 0),
          }))
          .sort((a, b) => new Date(b.orders[0]?.createdAt) - new Date(a.orders[0]?.createdAt));

        return {
          tableNumber: parseInt(tbl),
          receipts,
          totalRevenue: orders.reduce((sum, o) =>
            sum + (o.orderItems ?? []).reduce((s, i) => s + (Number(i.menuItem?.price ?? 0) * i.quantity), 0), 0),
        };
      })
      .sort((a, b) => {
        const aLatest = a.receipts[0]?.orders[0]?.createdAt;
        const bLatest = b.receipts[0]?.orders[0]?.createdAt;
        return new Date(bLatest) - new Date(aLatest);
      });
  }, [pastOrders]);

  return (
    <div>
      <h3 className="text-tac-muted text-[10px] font-mono font-bold uppercase tracking-[0.2em] mb-3">Past Orders by Table</h3>
      <div className="grid gap-3 md:grid-cols-2">
        {tableGroups.map(({ tableNumber: tbl, receipts, totalRevenue }) => (
          <div key={tbl} className="tac-panel border-l-2 border-l-tac-green/50 opacity-80">
            {/* Table header — clickable */}
            <button
              onClick={() => setExpandedTable(expandedTable === tbl ? null : tbl)}
              className="w-full flex items-center justify-between p-4 text-left hover:bg-tac-mid/30 transition"
            >
              <div className="flex items-center gap-3">
                <p className="text-xl font-bold text-tac-bright font-mono">TBL {tbl}</p>
                <span className="text-tac-muted text-xs font-mono">
                  {receipts.length} receipt{receipts.length !== 1 ? 's' : ''}
                </span>
              </div>
              <div className="flex items-center gap-3">
                <span className="text-tac-green font-mono font-bold text-sm">${totalRevenue.toFixed(2)}</span>
                <span className="text-tac-muted text-xs font-mono">{expandedTable === tbl ? '▼' : '▶'}</span>
              </div>
            </button>

            {/* Expanded: show receipts */}
            {expandedTable === tbl && (
              <div className="border-t border-tac-border px-4 pb-4 pt-3 space-y-3 animate-fade-in">
                {receipts.map(({ receiptNumber: rn, isLegacy, orders, total }) => {
                  const receiptKey = `${tbl}-${rn}`;
                  const isExpanded = expandedReceipt === receiptKey;
                  return (
                    <div key={rn} className="bg-tac-mid/30 rounded-tac border border-tac-border overflow-hidden">
                      {/* Receipt header */}
                      <button
                        onClick={() => setExpandedReceipt(isExpanded ? null : receiptKey)}
                        className="w-full flex items-center justify-between px-3 py-2.5 text-left hover:bg-tac-mid/40 transition"
                      >
                        <div className="flex items-center gap-2">
                          <span className="text-tac-cyan font-mono font-bold text-sm">
                            {isLegacy ? `ORD #${orders[0]?.id}` : `RCT #${rn}`}
                          </span>
                          <span className="text-tac-muted text-xs font-mono">
                            {orders.length} order{orders.length !== 1 ? 's' : ''}
                          </span>
                        </div>
                        <div className="flex items-center gap-2">
                          <span className="text-tac-green font-mono font-bold text-sm">${total.toFixed(2)}</span>
                          <span className="text-tac-muted text-xs font-mono">{isExpanded ? '▼' : '▶'}</span>
                        </div>
                      </button>

                      {/* Receipt orders */}
                      {isExpanded && (
                        <div className="border-t border-tac-border/50 px-3 pb-3 pt-2 space-y-2 animate-fade-in">
                          {orders.map((order) => {
                            const items = order.orderItems ?? [];
                            const orderTotal = items.reduce((s, i) => s + (Number(i.menuItem?.price ?? 0) * i.quantity), 0);
                            return (
                              <div key={order.id} className="bg-tac-dark/60 rounded-tac p-2.5 border border-tac-border/50">
                                <div className="flex items-center justify-between mb-1.5">
                                  <span className="text-tac-muted text-xs font-mono">ORD #{order.id}</span>
                                  <span className="text-tac-muted text-xs font-mono">
                                    {new Date(order.createdAt).toLocaleTimeString()}
                                  </span>
                                </div>
                                <ul className="space-y-0.5">
                                  {items.map((item, i) => (
                                    <li key={item.id ?? i} className="text-sm text-tac-muted flex justify-between">
                                      <span>
                                        <span className="font-mono">{item.quantity}x</span> {item.menuItem?.name ?? 'Unknown'}
                                        {item.specialInstructions && <span className="italic ml-1">— {item.specialInstructions}</span>}
                                      </span>
                                      <span className="font-mono ml-2">${(Number(item.menuItem?.price ?? 0) * item.quantity).toFixed(2)}</span>
                                    </li>
                                  ))}
                                </ul>
                                <div className="text-right text-xs text-tac-muted mt-1 pt-1 border-t border-tac-border/30 font-mono">
                                  ${orderTotal.toFixed(2)}
                                </div>
                              </div>
                            );
                          })}
                          {/* Print receipt button */}
                          <button
                            onClick={(e) => { e.stopPropagation(); onPrintReceipt(tbl, orders, total); }}
                            className="w-full min-h-[40px] tac-btn-muted text-xs font-mono uppercase tracking-wider mt-2"
                          >
                            PRINT RECEIPT
                          </button>
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}

// ── Main FOH Dashboard ──────────────────────────────────────────────────────
export default function FOHDashboard() {
  const { user, logout } = useAuth();
  const { toasts, showToast, dismissToast } = useToast();

  // Menu state
  const [menuItems, setMenuItems] = useState([]);
  const [menuLoading, setMenuLoading] = useState(true);
  const [activeCategory, setActiveCategory] = useState('All');
  const [selectedItem, setSelectedItem] = useState(null);

  // Cart state
  const [cart, setCart] = useState([]);
  const [tableNumber, setTableNumber] = useState('');

  // Order state
  const [submitting, setSubmitting] = useState(false);
  const [myOrders, setMyOrders] = useState([]);
  const [ordersLoading, setOrdersLoading] = useState(true);

  // Alerts
  const [alerts, setAlerts] = useState([]);
  const [showAlerts, setShowAlerts] = useState(false);

  // View toggle: 'menu' | 'orders' | 'tables'
  const [view, setView] = useState('menu');

  // Table detail modal
  const [selectedTable, setSelectedTable] = useState(null);

  const mounted = useRef(true);
  useEffect(() => { mounted.current = true; return () => { mounted.current = false; }; }, []);

  // ── Fetch menu ──────────────────────────────────────────────────────────
  useEffect(() => {
    axios.get('/api/menu')
      .then((res) => { if (mounted.current) setMenuItems(res.data.items ?? res.data ?? []); })
      .catch(() => { if (mounted.current) showToast('Failed to load menu', 'error'); })
      .finally(() => { if (mounted.current) setMenuLoading(false); });
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  // ── Fetch my orders ─────────────────────────────────────────────────────
  const fetchMyOrders = useCallback(() => {
    setOrdersLoading(true);
    axios.get('/api/orders/my')
      .then((res) => { if (mounted.current) setMyOrders(res.data.orders ?? res.data ?? []); })
      .catch(() => { if (mounted.current) showToast('Failed to load orders', 'error'); })
      .finally(() => { if (mounted.current) setOrdersLoading(false); });
  }, [showToast]);

  useEffect(() => { fetchMyOrders(); }, [fetchMyOrders]);

  // ── Socket listeners ────────────────────────────────────────────────────
  const [socketReady, setSocketReady] = useState(!!getSocket());
  useEffect(() => {
    if (socketReady) return;
    const id = setInterval(() => { if (getSocket()) { setSocketReady(true); clearInterval(id); } }, 500);
    return () => clearInterval(id);
  }, [socketReady]);

  useEffect(() => {
    const socket = getSocket();
    if (!socket) return;

    const onAlert = (alert) => {
      setAlerts((prev) => [alert, ...prev].slice(0, 20));
      if (alert.timestamp) recordAlertTimestamp(alert.timestamp);
      showToast(alert.message, 'warning', 5000);
    };

    const onMissedAlerts = (missed) => {
      if (!Array.isArray(missed) || missed.length === 0) return;
      setAlerts((prev) => [...missed.reverse(), ...prev].slice(0, 30));
      const latest = missed[missed.length - 1];
      if (latest?.timestamp) recordAlertTimestamp(latest.timestamp);
      showToast(`${missed.length} missed alert(s) while offline`, 'warning', 6000);
    };
    const onItemUnavailable = ({ name, menuItemId }) => {
      setMenuItems((prev) => prev.map((m) => m.id === menuItemId ? { ...m, availabilityStatus: 'UNAVAILABLE' } : m));
      showToast(`"${name}" is now unavailable`, 'warning', 4000);
    };
    const onItemAvailable = ({ name, menuItemId }) => {
      setMenuItems((prev) => prev.map((m) => m.id === menuItemId ? { ...m, availabilityStatus: 'AVAILABLE' } : m));
      showToast(`"${name}" is back in stock`, 'success', 3000);
    };
    const onOrderAcknowledged = ({ order }) => {
      setMyOrders((prev) => prev.map((o) => o.id === order.id ? { ...o, ...order } : o));
      showToast(`Table ${order.tableNumber} — order accepted by kitchen`, 'success');
    };
    const onOrderDelayed = ({ order, estimatedWait }) => {
      setMyOrders((prev) => prev.map((o) => o.id === order.id ? { ...o, ...order } : o));
      showToast(`Table ${order.tableNumber} — delayed ~${estimatedWait} min`, 'warning', 5000);
    };
    const onOrderCancelled = ({ order }) => {
      setMyOrders((prev) => prev.map((o) => o.id === order.id ? { ...o, ...order } : o));
      showToast(`Table ${order.tableNumber} — order cancelled by kitchen`, 'error', 5000);
    };
    const onOrderStatusChanged = (data) => {
      setMyOrders((prev) => prev.map((o) => o.id === (data.id ?? data.orderId) ? { ...o, ...data } : o));
    };
    const onOrderNote = ({ orderId, note, tableNumber: tbl }) => {
      setMyOrders((prev) => prev.map((o) => o.id === orderId ? { ...o, bohNote: note } : o));
      showToast(`Kitchen note for Table ${tbl}: ${note}`, 'warning', 6000);
    };

    const onMenuCreated = (item) => {
      setMenuItems((prev) => prev.find((m) => m.id === item.id) ? prev : [...prev, item]);
    };
    const onMenuUpdated = (item) => {
      setMenuItems((prev) => prev.map((m) => m.id === item.id ? { ...m, ...item } : m));
    };
    const onMenuDeleted = ({ id }) => {
      setMenuItems((prev) => prev.filter((m) => m.id !== id));
    };

    socket.on('alert:broadcast', onAlert);
    socket.on('alerts:missed', onMissedAlerts);
    socket.on('item:unavailable', onItemUnavailable);
    socket.on('item:available', onItemAvailable);
    socket.on('order:acknowledged', onOrderAcknowledged);
    socket.on('order:delayed', onOrderDelayed);
    socket.on('order:cancelled', onOrderCancelled);
    socket.on('order:statusChanged', onOrderStatusChanged);
    socket.on('order:note', onOrderNote);
    socket.on('menu:created', onMenuCreated);
    socket.on('menu:updated', onMenuUpdated);
    socket.on('menu:deleted', onMenuDeleted);

    return () => {
      socket.off('alert:broadcast', onAlert);
      socket.off('alerts:missed', onMissedAlerts);
      socket.off('item:unavailable', onItemUnavailable);
      socket.off('item:available', onItemAvailable);
      socket.off('order:acknowledged', onOrderAcknowledged);
      socket.off('order:delayed', onOrderDelayed);
      socket.off('order:cancelled', onOrderCancelled);
      socket.off('order:statusChanged', onOrderStatusChanged);
      socket.off('order:note', onOrderNote);
      socket.off('menu:created', onMenuCreated);
      socket.off('menu:updated', onMenuUpdated);
      socket.off('menu:deleted', onMenuDeleted);
    };
  }, [socketReady]); // eslint-disable-line react-hooks/exhaustive-deps

  // ── Polling fallback (when Socket.IO is unavailable, e.g. on Render) ──────
  useEffect(() => {
    const socket = getSocket();
    if (socket?.connected) return;

    const poll = () => {
      axios.get('/api/orders/my')
        .then((res) => { if (mounted.current) setMyOrders(res.data.orders ?? res.data ?? []); })
        .catch(() => {});
      axios.get('/api/menu')
        .then((res) => { if (mounted.current) setMenuItems(res.data.items ?? res.data ?? []); })
        .catch(() => {});
    };

    const id = setInterval(poll, 5000);
    return () => clearInterval(id);
  }, [socketReady]);

  // ── Active tables (tables with at least one non-completed/cancelled order) ──
  const activeTables = React.useMemo(() => {
    const tableMap = {};
    myOrders.forEach((order) => {
      const tbl = order.tableNumber;
      if (!tableMap[tbl]) tableMap[tbl] = [];
      tableMap[tbl].push(order);
    });
    // Only tables with at least one active order
    return Object.entries(tableMap)
      .filter(([, orders]) => orders.some((o) => ['PENDING', 'IN_PROGRESS', 'DELAYED'].includes(o.status)))
      .map(([tbl, orders]) => ({ tableNumber: parseInt(tbl), orders }))
      .sort((a, b) => a.tableNumber - b.tableNumber);
  }, [myOrders]);

  const activeTableNumbers = new Set(activeTables.map((t) => t.tableNumber));

  // ── Cart helpers ────────────────────────────────────────────────────────
  const addToCart = (item) => {
    setCart((prev) => {
      const existing = prev.find((c) => c.menuItemId === item.menuItemId && c.specialInstructions === item.specialInstructions);
      if (existing) return prev.map((c) => c === existing ? { ...c, quantity: c.quantity + item.quantity } : c);
      return [...prev, { ...item, cartId: Date.now() }];
    });
    showToast(`Added ${item.quantity}x ${item.name}`, 'success', 2000);
  };

  const removeFromCart = (cartId) => setCart((prev) => prev.filter((c) => c.cartId !== cartId));

  const cartTotal = cart.reduce((sum, c) => sum + Number(c.price) * c.quantity, 0);
  const cartCount = cart.reduce((sum, c) => sum + c.quantity, 0);

  // ── Submit order ────────────────────────────────────────────────────────
  const handleSubmit = async () => {
    if (cart.length === 0) {
      showToast('Please add at least one item before submitting.', 'error');
      return;
    }
    if (!tableNumber) {
      showToast('Please select a valid table number.', 'error');
      return;
    }
    const unavailableInCart = cart.filter((c) => {
      const menu = menuItems.find((m) => m.id === c.menuItemId);
      return menu && menu.availabilityStatus === 'UNAVAILABLE';
    });
    if (unavailableInCart.length > 0) {
      showToast(`Some items are no longer available: ${unavailableInCart.map((c) => c.name).join(', ')}. Please remove them before submitting.`, 'error', 6000);
      return;
    }
    setSubmitting(true);
    try {
      const res = await axios.post('/api/orders', {
        tableNumber: parseInt(tableNumber),
        items: cart.map((c) => ({ menuItemId: c.menuItemId, quantity: c.quantity, specialInstructions: c.specialInstructions })),
      });
      const newOrder = res.data;
      setMyOrders((prev) => [newOrder, ...prev]);
      setCart([]);
      setTableNumber('');
      showToast('Order sent successfully!', 'success');
      setView('tables');
    } catch (err) {
      const msg = err.response?.data?.error || 'Network error — your order has been saved. Please try again.';
      showToast(msg, 'error', 6000);
    } finally {
      setSubmitting(false);
    }
  };

  // ── Table actions ───────────────────────────────────────────────────────
  const handleAddFood = (tableNum) => {
    setSelectedTable(null);
    setTableNumber(String(tableNum));
    setView('menu');
  };

  const handleCheckout = async (tableNum, paymentMethod, cardLast4) => {
    const tableOrders = myOrders.filter(
      (o) => o.tableNumber === tableNum && ['PENDING', 'IN_PROGRESS', 'DELAYED'].includes(o.status)
    );
    if (tableOrders.length === 0) return;

    try {
      // Get a shared receipt number for this checkout batch
      const receiptRes = await axios.get('/api/orders/next-receipt');
      const receiptNumber = receiptRes.data.receiptNumber;

      await Promise.all(
        tableOrders.map((o) =>
          axios.patch(`/api/orders/${o.id}/status`, { status: 'COMPLETED', paymentMethod, receiptNumber, cardLast4 }).catch(() => null)
        )
      );
      setMyOrders((prev) =>
        prev.map((o) =>
          o.tableNumber === tableNum && ['PENDING', 'IN_PROGRESS', 'DELAYED'].includes(o.status)
            ? { ...o, status: 'COMPLETED', receiptNumber, paymentMethod, cardLast4 }
            : o
        )
      );
      setSelectedTable(null);
      showToast(`Table ${tableNum} checked out — Receipt #${receiptNumber}`, 'success');
    } catch {
      showToast('Failed to checkout table. Please try again.', 'error');
    }
  };

  const handlePrintReceipt = (tableNum, orders, total) => {
    const receiptWindow = window.open('', '_blank', 'width=400,height=600');
    if (!receiptWindow) {
      showToast('Please allow popups to print receipts.', 'error');
      return;
    }

    const allItems = orders.flatMap((o) =>
      (o.orderItems ?? []).map((item) => ({
        name: item.menuItem?.name ?? 'Unknown',
        qty: item.quantity,
        price: Number(item.menuItem?.price ?? 0) * item.quantity,
      }))
    );

    const now = new Date().toLocaleString();

    // Get payment info from orders (all orders in a checkout share the same payment)
    const firstOrder = orders[0] ?? {};
    const payMethod = firstOrder.paymentMethod ?? 'CASH';
    const last4 = firstOrder.cardLast4 ?? null;
    const receiptNum = firstOrder.receiptNumber ?? null;

    let paymentLine = payMethod.replace('_', ' ');
    if (last4 && (payMethod === 'CREDIT' || payMethod === 'DEBIT')) {
      paymentLine += ` — XXXX XXXX XXXX ${last4}`;
    } else if (last4 && payMethod === 'GIFT_CARD') {
      paymentLine += ` — ****${last4}`;
    }

    receiptWindow.document.write(`
      <html>
      <head><title>Receipt - Table ${tableNum}</title>
      <style>
        body { font-family: 'Courier New', monospace; max-width: 300px; margin: 20px auto; color: #000; }
        h1 { text-align: center; font-size: 20px; margin-bottom: 4px; }
        .subtitle { text-align: center; font-size: 12px; color: #666; margin-bottom: 16px; }
        .divider { border-top: 1px dashed #999; margin: 12px 0; }
        .item { display: flex; justify-content: space-between; font-size: 14px; margin: 4px 0; }
        .total { display: flex; justify-content: space-between; font-size: 18px; font-weight: bold; margin-top: 8px; }
        .payment { text-align: center; font-size: 13px; margin-top: 8px; }
        .receipt-num { text-align: center; font-size: 11px; color: #666; margin-top: 4px; }
        .footer { text-align: center; font-size: 11px; color: #999; margin-top: 20px; }
        @media print { body { margin: 0; } }
      </style></head>
      <body>
        <h1>SOS Restaurant</h1>
        <p class="subtitle">Table ${tableNum} — ${now}</p>
        <div class="divider"></div>
        ${allItems.map((item) => `
          <div class="item">
            <span>${item.qty}x ${item.name}</span>
            <span>$${item.price.toFixed(2)}</span>
          </div>
        `).join('')}
        <div class="divider"></div>
        <div class="total">
          <span>TOTAL</span>
          <span>$${total.toFixed(2)}</span>
        </div>
        <p class="payment">${paymentLine}</p>
        ${receiptNum ? `<p class="receipt-num">Receipt #${receiptNum}</p>` : ''}
        <div class="divider"></div>
        <p class="footer">Thank you for dining with us!</p>
        <script>window.onload = function() { window.print(); }</script>
      </body></html>
    `);
    receiptWindow.document.close();
  };

  // ── Categories ──────────────────────────────────────────────────────────
  const visibleMenu = menuItems.filter(
    (item) => item.availabilityStatus !== 'UNAVAILABLE' || item.inventoryItemId != null
  );
  const categories = ['All', ...new Set(visibleMenu.map((m) => m.category || 'Other'))];
  const filteredMenu = activeCategory === 'All' ? visibleMenu : visibleMenu.filter((m) => (m.category || 'Other') === activeCategory);

  // ── Active / past orders ────────────────────────────────────────────────
  const activeOrders = myOrders.filter((o) => ['PENDING', 'IN_PROGRESS', 'DELAYED'].includes(o.status));
  const pastOrders = myOrders.filter((o) => ['COMPLETED', 'CANCELLED'].includes(o.status));

  return (
    <div className="min-h-screen bg-tac-darker flex flex-col">
      <ToastContainer toasts={toasts} onDismiss={dismissToast} />
      {selectedItem && <ItemModal item={selectedItem} onAdd={addToCart} onClose={() => setSelectedItem(null)} />}
      {selectedTable != null && (
        <TableModal
          tableNum={selectedTable}
          orders={myOrders.filter((o) => o.tableNumber === selectedTable && ['PENDING', 'IN_PROGRESS', 'DELAYED'].includes(o.status))}
          onClose={() => setSelectedTable(null)}
          onAddFood={handleAddFood}
          onCheckout={handleCheckout}
          onPrintReceipt={handlePrintReceipt}
        />
      )}

      {/* ── Status Bar / Navbar ── */}
      <nav className="bg-tac-black border-b border-tac-border px-4 py-3 flex items-center justify-between sticky top-0 z-10">
        <div className="flex items-center gap-3">
          <span className="text-tac-green font-mono font-black text-xl tracking-widest">SOS</span>
          <span className="text-tac-border">|</span>
          <span className="text-tac-muted font-mono text-xs uppercase tracking-widest hidden sm:block">Floor Ops — {user?.name}</span>
        </div>
        <div className="flex items-center gap-2">
          <button onClick={() => setShowAlerts(!showAlerts)}
            className="relative min-h-[48px] min-w-[48px] flex items-center justify-center rounded-tac bg-tac-mid hover:bg-tac-border text-tac-text transition border border-tac-border">
            <span className="font-mono text-sm">ALT</span>
            {alerts.length > 0 && (
              <span className="absolute -top-1 -right-1 bg-tac-red/80 text-white text-xs font-mono font-bold rounded-tac w-5 h-5 flex items-center justify-center">
                {alerts.length}
              </span>
            )}
          </button>
          <button onClick={logout}
            className="min-h-[48px] px-4 tac-btn-danger">
            LOGOUT
          </button>
        </div>
      </nav>

      {/* Alert dropdown */}
      {showAlerts && alerts.length > 0 && (
        <div className="bg-tac-dark border-b border-tac-border px-4 py-3 max-h-56 overflow-y-auto animate-slide-down">
          <div className="flex items-center justify-between mb-2">
            <h3 className="text-tac-muted text-[10px] font-mono font-bold uppercase tracking-[0.2em]">Incoming Alerts</h3>
            <button onClick={() => { setAlerts([]); setShowAlerts(false); }}
              className="text-tac-muted hover:text-tac-text text-xs font-mono transition">CLEAR ALL</button>
          </div>
          {alerts.slice(0, 15).map((a, i) => (
            <div key={a.id || i} className="flex items-start justify-between gap-2 bg-tac-mid/60 rounded-tac px-3 py-2 mb-1 border border-tac-border">
              <div className="flex-1 min-w-0">
                {a.sender?.name && <span className="text-tac-amber text-xs font-mono font-semibold mr-1">{a.sender.name}:</span>}
                <span className="text-sm text-tac-text">{a.message}</span>
                {a.timestamp && <span className="text-tac-muted text-xs font-mono ml-2">{new Date(a.timestamp).toLocaleTimeString()}</span>}
              </div>
              <button onClick={() => setAlerts((prev) => prev.filter((_, idx) => idx !== i))}
                className="text-tac-muted hover:text-tac-red text-xs font-bold shrink-0 mt-0.5 transition">✕</button>
            </div>
          ))}
        </div>
      )}

      {/* ── Tab bar ── */}
      <div className="bg-tac-black/50 border-b border-tac-border px-4 flex items-center gap-1 pt-1">
        <button onClick={() => setView('menu')}
          className={`flex items-center gap-2 px-4 py-2.5 text-xs font-mono font-bold uppercase tracking-widest transition border-b-2 ${view === 'menu' ? 'text-tac-green border-tac-green' : 'text-tac-muted border-transparent hover:text-tac-text'}`}>
          New Order
          {cartCount > 0 && <span className="bg-tac-green/20 text-tac-lime border border-tac-green/30 text-xs font-bold rounded-tac px-2 py-0.5">{cartCount}</span>}
        </button>
        <button onClick={() => setView('tables')}
          className={`flex items-center gap-2 px-4 py-2.5 text-xs font-mono font-bold uppercase tracking-widest transition border-b-2 ${view === 'tables' ? 'text-tac-green border-tac-green' : 'text-tac-muted border-transparent hover:text-tac-text'}`}>
          Tables
          {activeTables.length > 0 && <span className="bg-tac-green/20 text-tac-lime border border-tac-green/30 text-xs font-bold rounded-tac px-2 py-0.5">{activeTables.length}</span>}
        </button>
        <button onClick={() => setView('orders')}
          className={`flex items-center gap-2 px-4 py-2.5 text-xs font-mono font-bold uppercase tracking-widest transition border-b-2 ${view === 'orders' ? 'text-tac-green border-tac-green' : 'text-tac-muted border-transparent hover:text-tac-text'}`}>
          My Orders
          {activeOrders.length > 0 && <span className="bg-tac-amber/20 text-amber-300 border border-tac-amber/30 text-xs font-bold rounded-tac px-2 py-0.5">{activeOrders.length}</span>}
        </button>
      </div>

      {/* ── Menu + Cart view ── */}
      {view === 'menu' && (
        <div className="flex-1 flex overflow-hidden">
          {/* Left: Menu browser */}
          <section className="flex-1 flex flex-col min-w-0 border-r border-tac-border">
            {/* Table picker + category tabs */}
            <div className="px-4 pt-3 pb-2 border-b border-tac-border">
              <div className="flex items-center gap-3 mb-3">
                <label className="text-tac-muted text-xs font-mono uppercase tracking-widest whitespace-nowrap">TBL #</label>
                <div className="relative">
                  <input type="number" min="1" max="99" value={tableNumber}
                    onChange={(e) => {
                      const val = e.target.value;
                      if (val && activeTableNumbers.has(parseInt(val))) {
                        showToast(`Table ${val} already has an active order`, 'error', 3000);
                        return;
                      }
                      setTableNumber(val);
                    }}
                    placeholder="—"
                    className="w-20 tac-input text-center px-3 py-2 text-lg font-bold" />
                </div>
                {tableNumber && activeTableNumbers.has(parseInt(tableNumber)) && (
                  <span className="text-red-400 text-xs font-mono">[!] TABLE IN USE</span>
                )}
              </div>
              <div className="flex gap-1.5 overflow-x-auto pb-1 scrollbar-none">
                {categories.map((cat) => (
                  <button key={cat} onClick={() => setActiveCategory(cat)}
                    className={`whitespace-nowrap px-3 py-1.5 rounded-tac text-xs font-mono font-semibold transition border ${activeCategory === cat ? 'bg-tac-green/15 text-tac-lime border-tac-green/30' : 'bg-tac-mid text-tac-text border-tac-border hover:bg-tac-border'}`}>
                    {CAT_ICONS[cat] ?? ''} {cat}
                  </button>
                ))}
              </div>
            </div>

            {/* Item grid */}
            <div className="flex-1 overflow-y-auto p-4">
              {menuLoading ? (
                <div className="flex items-center justify-center h-40"><Spinner /></div>
              ) : filteredMenu.length === 0 ? (
                <EmptyState message="NO ITEMS IN THIS CATEGORY" />
              ) : (
                <div className="grid grid-cols-2 lg:grid-cols-3 gap-3">
                  {filteredMenu.map((item) => {
                    const unavailable = item.availabilityStatus === 'UNAVAILABLE';
                    return (
                      <button key={item.id}
                        onClick={() => !unavailable && setSelectedItem(item)}
                        disabled={unavailable}
                        className={`text-left rounded-tac p-4 transition border ${unavailable
                          ? 'bg-tac-dark/40 border-tac-border/40 opacity-50 cursor-not-allowed'
                          : 'bg-tac-dark border-tac-border hover:border-tac-green/50 hover:shadow-tac active:scale-[0.98]'}`}>
                        <p className="text-tac-bright font-semibold text-sm mb-1 leading-snug">{item.name}</p>
                        <p className="text-tac-green font-bold font-mono">${Number(item.price).toFixed(2)}</p>
                        {unavailable && <p className="text-red-400 text-xs font-mono font-semibold mt-1">OFFLINE</p>}
                      </button>
                    );
                  })}
                </div>
              )}
            </div>
          </section>

          {/* Right: Cart */}
          <section className="w-80 lg:w-96 flex flex-col bg-tac-dark/30">
            <div className="px-4 pt-4 pb-2 border-b border-tac-border">
              <h2 className="text-tac-green font-mono font-bold text-sm uppercase tracking-widest">
                Cart {tableNumber && <span className="text-tac-muted font-normal ml-1">(TBL {tableNumber})</span>}
              </h2>
            </div>

            <div className="flex-1 overflow-y-auto px-4 py-3">
              {cart.length === 0 ? (
                <EmptyState message="TAP A MENU ITEM TO ADD" />
              ) : (
                <div className="space-y-2">
                  {cart.map((c) => (
                    <div key={c.cartId} className="tac-panel p-3 flex items-start justify-between gap-2">
                      <div className="min-w-0 flex-1">
                        <p className="text-tac-bright text-sm font-semibold"><span className="font-mono">{c.quantity}x</span> {c.name}</p>
                        {c.specialInstructions && <p className="text-tac-muted text-xs italic mt-0.5">{c.specialInstructions}</p>}
                        <p className="text-tac-muted text-xs mt-0.5 font-mono">${(Number(c.price) * c.quantity).toFixed(2)}</p>
                      </div>
                      <button onClick={() => removeFromCart(c.cartId)}
                        className="text-tac-muted hover:text-tac-red text-lg leading-none mt-0.5">✕</button>
                    </div>
                  ))}
                </div>
              )}
            </div>

            {/* Cart footer */}
            <div className="border-t border-tac-border px-4 py-4">
              <div className="flex justify-between text-tac-bright font-bold text-lg mb-3 font-mono">
                <span>TOTAL</span>
                <span>${cartTotal.toFixed(2)}</span>
              </div>
              <button onClick={handleSubmit}
                disabled={submitting || cart.length === 0 || !tableNumber}
                className="w-full min-h-[52px] tac-btn text-base disabled:opacity-50 disabled:cursor-not-allowed">
                {submitting ? <Spinner size="sm" /> : 'SUBMIT ORDER'}
              </button>
            </div>
          </section>
        </div>
      )}

      {/* ── Tables view ── */}
      {view === 'tables' && (
        <div className="flex-1 overflow-y-auto p-6">
          {activeTables.length === 0 ? (
            <EmptyState message="NO ACTIVE TABLES — CREATE AN ORDER TO BEGIN" />
          ) : (
            <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-5">
              {activeTables.map(({ tableNumber: tbl, orders }) => {
                const activeCount = orders.filter((o) => ['PENDING', 'IN_PROGRESS', 'DELAYED'].includes(o.status)).length;
                const hasDelayed = orders.some((o) => o.status === 'DELAYED');
                const hasPending = orders.some((o) => o.status === 'PENDING');
                const borderColor = hasDelayed ? 'border-tac-red' : hasPending ? 'border-tac-amber' : 'border-tac-green';

                return (
                  <button
                    key={tbl}
                    onClick={() => setSelectedTable(tbl)}
                    className={`group tac-panel border-2 ${borderColor} p-4 flex flex-col items-center gap-2 hover:shadow-tac-glow hover:scale-[1.03] active:scale-[0.97] transition-all`}
                  >
                    <img src={tableImage} alt={`Table ${tbl}`} className="w-16 h-16 opacity-70 group-hover:opacity-100 transition-opacity" />
                    <span className="text-tac-bright font-black text-xl font-mono">TBL {tbl}</span>
                    <span className="text-tac-muted text-xs font-mono">
                      {activeCount} active order{activeCount !== 1 ? 's' : ''}
                    </span>
                    {hasDelayed && <span className="text-red-400 text-xs font-mono font-semibold">[!] DELAYED</span>}
                  </button>
                );
              })}
            </div>
          )}
        </div>
      )}

      {/* ── My Orders view ── */}
      {view === 'orders' && (
        <div className="flex-1 overflow-y-auto p-4">
          {ordersLoading ? (
            <div className="flex items-center justify-center h-40"><Spinner /></div>
          ) : myOrders.length === 0 ? (
            <EmptyState message="NO ORDERS SUBMITTED YET" />
          ) : (
            <>
              {/* Active orders */}
              {activeOrders.length > 0 && (
                <div className="mb-6">
                  <h3 className="text-tac-muted text-[10px] font-mono font-bold uppercase tracking-[0.2em] mb-3">Active Orders</h3>
                  <div className="grid gap-3 md:grid-cols-2">
                    {activeOrders.map((order) => {
                      const items = order.orderItems ?? [];
                      const BORDER = { PENDING: 'border-l-tac-amber', IN_PROGRESS: 'border-l-tac-cyan', DELAYED: 'border-l-tac-red' };
                      return (
                        <div key={order.id} className={`tac-panel border-l-2 ${BORDER[order.status] ?? 'border-l-tac-border'} p-4 shadow-tac`}>
                          <div className="flex items-center justify-between mb-2">
                            <p className="text-2xl font-black text-tac-bright font-mono">TBL {order.tableNumber}</p>
                            <div className="flex items-center gap-2">
                              <TimeElapsed createdAt={order.createdAt} />
                              <StatusBadge status={order.status} />
                            </div>
                          </div>
                          {order.status === 'DELAYED' && order.estimatedWait && (
                            <p className="text-amber-300 text-sm font-mono font-semibold mb-2">Est. ~{order.estimatedWait} min wait</p>
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
                        </div>
                      );
                    })}
                  </div>
                </div>
              )}

              {/* Past orders — grouped by table */}
              {pastOrders.length > 0 && (
                <PastOrdersByTable pastOrders={pastOrders} onPrintReceipt={handlePrintReceipt} />
              )}
            </>
          )}
        </div>
      )}
    </div>
  );
}
