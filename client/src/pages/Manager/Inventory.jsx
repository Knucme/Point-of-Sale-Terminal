import React, { useState, useEffect, useCallback } from 'react';
import axios from 'axios';
import { useNavigate } from 'react-router-dom';
import { getSocket } from '../../socket';
import Spinner from '../../components/Spinner';
import EmptyState from '../../components/EmptyState';
import ConfirmModal from '../../components/ConfirmModal';
import { useToast } from '../../components/useToast';
import { ToastContainer } from '../../components/Toast';

const CATEGORIES = ['Mains', 'Sides', 'Appetizers', 'Drinks', 'Desserts', 'Other'];

// ── Add / Edit modal ────────────────────────────────────────────────────────
function ItemModal({ item, onSave, onClose }) {
  const isEdit = !!item;
  const [form, setForm] = useState({
    itemName: item?.itemName ?? '',
    quantity: item?.quantity ?? 0,
    unit: item?.unit ?? 'qty',
    lowStockThreshold: item?.lowStockThreshold ?? 3,
    restockAmount: item?.restockAmount ?? 10,
    price: '',
    category: 'Mains',
  });
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  const handleSubmit = async () => {
    if (!form.itemName.trim()) { setError('Item name is required.'); return; }
    if (!isEdit && (!form.price || isNaN(parseFloat(form.price)) || parseFloat(form.price) < 0)) {
      setError('A valid menu price is required for new items.'); return;
    }
    setSaving(true);
    setError('');
    await onSave(form, item?.id);
    setSaving(false);
  };

  const set = (k, v) => { setForm((p) => ({ ...p, [k]: v })); setError(''); };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4">
      <div className="tac-panel shadow-tac-glow max-w-md w-full p-6 animate-fade-in max-h-[90vh] overflow-y-auto">
        <h2 className="text-lg font-bold text-tac-bright font-mono uppercase tracking-wide mb-4">{isEdit ? 'Edit Item' : 'Add New Item'}</h2>

        <label className="text-tac-muted text-xs font-mono uppercase tracking-widest mb-1 block">Item Name</label>
        <input value={form.itemName} onChange={(e) => set('itemName', e.target.value)}
          placeholder="e.g. Duck Breast"
          className="w-full tac-input px-3 py-2 text-sm mb-3" />

        {!isEdit && (
          <>
            <div className="grid grid-cols-2 gap-3 mb-3">
              <div>
                <label className="text-tac-muted text-xs font-mono uppercase tracking-widest mb-1 block">Menu Price ($)</label>
                <input type="number" min="0" step="0.01" value={form.price} onChange={(e) => set('price', e.target.value)}
                  placeholder="12.99"
                  className="w-full tac-input px-3 py-2 text-sm" />
              </div>
              <div>
                <label className="text-tac-muted text-xs font-mono uppercase tracking-widest mb-1 block">Category</label>
                <select value={form.category} onChange={(e) => set('category', e.target.value)}
                  className="w-full tac-input px-3 py-2 text-sm">
                  {CATEGORIES.map((c) => <option key={c} value={c}>{c}</option>)}
                </select>
              </div>
            </div>
            <p className="text-tac-muted text-[10px] font-mono mb-3 uppercase tracking-wider">Creates inventory + menu item visible to BOH/FOH</p>
          </>
        )}

        <div className="grid grid-cols-2 gap-3 mb-3">
          <div>
            <label className="text-tac-muted text-xs font-mono uppercase tracking-widest mb-1 block">Stock Qty</label>
            <input type="number" min="0" value={form.quantity} onChange={(e) => set('quantity', e.target.value)}
              className="w-full tac-input px-3 py-2 text-sm" />
          </div>
          <div>
            <label className="text-tac-muted text-xs font-mono uppercase tracking-widest mb-1 block">Unit</label>
            <input value={form.unit} onChange={(e) => set('unit', e.target.value)}
              className="w-full tac-input px-3 py-2 text-sm" />
          </div>
        </div>

        <div className="grid grid-cols-2 gap-3 mb-4">
          <div>
            <label className="text-tac-muted text-xs font-mono uppercase tracking-widest mb-1 block">Low Threshold</label>
            <input type="number" min="0" value={form.lowStockThreshold} onChange={(e) => set('lowStockThreshold', e.target.value)}
              className="w-full tac-input px-3 py-2 text-sm" />
          </div>
          <div>
            <label className="text-tac-muted text-xs font-mono uppercase tracking-widest mb-1 block">Restock Amt</label>
            <input type="number" min="1" value={form.restockAmount} onChange={(e) => set('restockAmount', e.target.value)}
              className="w-full tac-input px-3 py-2 text-sm" />
          </div>
        </div>

        {error && <p className="text-red-400 text-xs font-mono mb-3">[ERR] {error}</p>}

        <div className="flex gap-3 justify-end">
          <button onClick={onClose} className="min-h-[48px] px-5 tac-btn-muted">CANCEL</button>
          <button onClick={handleSubmit} disabled={saving || !form.itemName.trim()}
            className="min-h-[48px] px-5 tac-btn disabled:opacity-50">
            {saving ? <Spinner size="sm" /> : isEdit ? 'SAVE' : 'CREATE'}
          </button>
        </div>
      </div>
    </div>
  );
}

// ── Main ────────────────────────────────────────────────────────────────────
export default function InventoryPage() {
  const navigate = useNavigate();
  const { toasts, showToast, dismissToast } = useToast();

  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [editItem, setEditItem] = useState(null);
  const [deleteTarget, setDeleteTarget] = useState(null);
  const [restockBusy, setRestockBusy] = useState({});

  const fetchItems = useCallback(async () => {
    try {
      const res = await axios.get('/api/inventory');
      setItems(res.data);
    } catch { showToast('Failed to load inventory', 'error'); }
    finally { setLoading(false); }
  }, [showToast]);

  useEffect(() => { fetchItems(); }, [fetchItems]);

  // Live socket updates
  useEffect(() => {
    const socket = getSocket();
    if (!socket) return;
    const onUpdate = (updated) => {
      setItems((prev) => prev.map((item) => {
        const u = updated.find((x) => x.id === item.id);
        return u ? { ...item, ...u, isLowStock: Number(u.quantity) <= Number(u.lowStockThreshold ?? item.lowStockThreshold) } : item;
      }));
    };
    socket.on('inventory:updated', onUpdate);
    return () => socket.off('inventory:updated', onUpdate);
  }, []);

  const handleSave = async (form, id) => {
    try {
      if (id) {
        const res = await axios.patch(`/api/inventory/${id}`, {
          itemName: form.itemName, quantity: form.quantity, unit: form.unit,
          lowStockThreshold: form.lowStockThreshold, restockAmount: form.restockAmount,
        });
        setItems((prev) => prev.map((item) => item.id === id ? res.data : item));
        showToast('Item updated', 'success');
      } else {
        const invRes = await axios.post('/api/inventory', {
          itemName: form.itemName, quantity: form.quantity, unit: form.unit,
          lowStockThreshold: form.lowStockThreshold, restockAmount: form.restockAmount,
        });
        setItems((prev) => [...prev, { ...invRes.data, isLowStock: Number(invRes.data.quantity) <= Number(invRes.data.lowStockThreshold) }]);
        try {
          await axios.post('/api/menu', {
            name: form.itemName,
            category: form.category || 'Other',
            price: parseFloat(form.price) || 0,
            inventoryItemId: invRes.data.id,
          });
        } catch (menuErr) {
          console.error('Menu item creation failed:', menuErr);
          showToast('Inventory created but menu item failed — check manually', 'warning');
        }
        showToast('Item created and added to menu', 'success');
      }
      setEditItem(null);
    } catch (err) {
      showToast(err.response?.data?.error || 'Save failed', 'error');
    }
  };

  const handleDelete = async () => {
    if (!deleteTarget) return;
    try {
      await axios.delete(`/api/inventory/${deleteTarget.id}`);
      setItems((prev) => prev.filter((i) => i.id !== deleteTarget.id));
      showToast('Item deleted', 'success');
    } catch (err) { showToast(err.response?.data?.error || 'Delete failed', 'error'); }
    finally { setDeleteTarget(null); }
  };

  const handleRestock = async (item) => {
    setRestockBusy((p) => ({ ...p, [item.id]: true }));
    try {
      const res = await axios.post(`/api/inventory/${item.id}/restock`);
      setItems((prev) => prev.map((i) => i.id === item.id
        ? { ...i, quantity: res.data.quantity, isLowStock: Number(res.data.quantity) <= Number(i.lowStockThreshold) }
        : i));
      showToast(`Restocked +${Number(res.data.restocked)}`, 'success');
    } catch { showToast('Restock failed', 'error'); }
    finally { setRestockBusy((p) => ({ ...p, [item.id]: false })); }
  };

  return (
    <div className="min-h-screen bg-tac-darker text-tac-text p-4 md:p-6">
      <ToastContainer toasts={toasts} onDismiss={dismissToast} />
      {editItem !== null && <ItemModal item={editItem.id ? editItem : null} onSave={handleSave} onClose={() => setEditItem(null)} />}
      {deleteTarget && (
        <ConfirmModal title="Delete Item" message={`Delete "${deleteTarget.itemName}"? This cannot be undone.`}
          confirmLabel="DELETE" danger onConfirm={handleDelete} onCancel={() => setDeleteTarget(null)} />
      )}

      <header className="flex items-center justify-between mb-6">
        <div>
          <button onClick={() => navigate('/manager')} className="text-tac-muted hover:text-tac-green text-xs font-mono mb-1 block uppercase tracking-widest">← Back to Command Center</button>
          <h1 className="text-2xl font-black tracking-tight font-mono text-tac-bright">Inventory Management</h1>
        </div>
        <button onClick={() => setEditItem({})}
          className="min-h-[48px] px-5 tac-btn">
          + ADD ITEM
        </button>
      </header>

      {loading ? (
        <div className="flex justify-center py-20"><Spinner /></div>
      ) : items.length === 0 ? (
        <EmptyState message="NO INVENTORY ITEMS YET" />
      ) : (
        <div className="tac-panel overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-tac-border text-tac-muted text-[10px] font-mono uppercase tracking-[0.15em]">
                  <th className="text-left px-4 py-3">Item</th>
                  <th className="text-right px-4 py-3">Qty</th>
                  <th className="text-left px-4 py-3">Unit</th>
                  <th className="text-right px-4 py-3">Low Threshold</th>
                  <th className="text-center px-4 py-3">Status</th>
                  <th className="text-right px-4 py-3">Actions</th>
                </tr>
              </thead>
              <tbody>
                {items.map((item) => {
                  const qty = Number(item.quantity);
                  const low = item.isLowStock;
                  const out = qty <= 0;
                  return (
                    <tr key={item.id} className={`border-b border-tac-border/50 ${out ? 'bg-tac-red/5' : low ? 'bg-tac-amber/5' : ''}`}>
                      <td className="px-4 py-3 font-medium text-tac-bright">{item.itemName}</td>
                      <td className={`px-4 py-3 text-right font-mono font-bold ${out ? 'text-red-400' : low ? 'text-amber-400' : 'text-tac-text'}`}>{qty}</td>
                      <td className="px-4 py-3 text-tac-muted font-mono">{item.unit}</td>
                      <td className="px-4 py-3 text-right text-tac-muted font-mono">{Number(item.lowStockThreshold)}</td>
                      <td className="px-4 py-3 text-center">
                        {out ? (
                          <span className="text-red-400 text-xs font-mono font-bold bg-tac-red/15 px-2 py-0.5 rounded-tac border border-tac-red/30">OUT</span>
                        ) : low ? (
                          <span className="text-amber-400 text-xs font-mono font-bold bg-tac-amber/15 px-2 py-0.5 rounded-tac border border-tac-amber/30">LOW</span>
                        ) : (
                          <span className="text-tac-green text-xs font-mono font-bold bg-tac-green/15 px-2 py-0.5 rounded-tac border border-tac-green/30">OK</span>
                        )}
                      </td>
                      <td className="px-4 py-3 text-right">
                        <div className="flex items-center justify-end gap-1.5">
                          <button onClick={() => handleRestock(item)} disabled={restockBusy[item.id]}
                            className="px-3 py-1.5 rounded-tac text-xs font-mono font-bold tac-btn disabled:opacity-50">
                            {restockBusy[item.id] ? '...' : `+${Number(item.restockAmount ?? 10)}`}
                          </button>
                          <button onClick={() => setEditItem(item)}
                            className="px-3 py-1.5 rounded-tac text-xs font-mono font-bold tac-btn-muted">EDIT</button>
                          <button onClick={() => setDeleteTarget(item)}
                            className="px-3 py-1.5 rounded-tac text-xs font-mono font-bold tac-btn-danger">DEL</button>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
}
