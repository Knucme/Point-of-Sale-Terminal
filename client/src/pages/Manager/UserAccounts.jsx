import React, { useState, useEffect, useCallback } from 'react';
import axios from 'axios';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import RoleBadge from '../../components/RoleBadge';
import Spinner from '../../components/Spinner';
import EmptyState from '../../components/EmptyState';
import ConfirmModal from '../../components/ConfirmModal';
import { useToast } from '../../components/useToast';
import { ToastContainer } from '../../components/Toast';

const ROLES = ['BOH', 'FOH', 'MANAGER'];

// ── Add / Edit User Modal ───────────────────────────────────────────────────
function UserModal({ target, onSave, onClose }) {
  const isEdit = !!target;
  const [form, setForm] = useState({
    name: target?.name ?? '',
    username: target?.username ?? '',
    password: '',
    role: target?.role ?? 'FOH',
  });
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  const set = (k, v) => { setForm((p) => ({ ...p, [k]: v })); setError(''); };

  const handleSubmit = async () => {
    if (!isEdit && (!form.name || !form.username || !form.password)) {
      setError('All fields are required.'); return;
    }
    if (!isEdit && form.password.length < 12) {
      setError('Password must be at least 12 characters.'); return;
    }
    if (!isEdit && (!/[A-Za-z]/.test(form.password) || !/[0-9]/.test(form.password))) {
      setError('Password must contain at least one letter and one digit.'); return;
    }
    setSaving(true);
    const ok = await onSave(form, target?.id);
    setSaving(false);
    if (ok) onClose();
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4">
      <div className="tac-panel shadow-tac-glow max-w-md w-full p-6 animate-fade-in">
        <h2 className="text-lg font-bold text-tac-bright font-mono uppercase tracking-wide mb-4">{isEdit ? 'Edit User' : 'Create User'}</h2>

        {!isEdit && (
          <>
            <label className="text-tac-muted text-xs font-mono uppercase tracking-widest mb-1 block">Full Name</label>
            <input value={form.name} onChange={(e) => set('name', e.target.value)}
              className="w-full tac-input px-3 py-2 text-sm mb-3" />

            <label className="text-tac-muted text-xs font-mono uppercase tracking-widest mb-1 block">Username</label>
            <input value={form.username} onChange={(e) => set('username', e.target.value)}
              className="w-full tac-input px-3 py-2 text-sm mb-3" />
          </>
        )}

        {!isEdit && (
          <>
            <label className="text-tac-muted text-xs font-mono uppercase tracking-widest mb-1 block">Password</label>
            <input type="password" value={form.password} onChange={(e) => set('password', e.target.value)}
              placeholder="≥12 chars, letter + digit"
              className="w-full tac-input px-3 py-2 text-sm mb-3" />
          </>
        )}

        <label className="text-tac-muted text-xs font-mono uppercase tracking-widest mb-1 block">Role</label>
        <div className="flex gap-2 mb-4">
          {ROLES.map((r) => (
            <button key={r} onClick={() => set('role', r)}
              className={`flex-1 min-h-[40px] rounded-tac text-xs font-mono font-bold transition border ${form.role === r ? 'bg-tac-green/15 text-tac-lime border-tac-green/30' : 'bg-tac-mid text-tac-text border-tac-border hover:bg-tac-border'}`}>
              {r}
            </button>
          ))}
        </div>

        {error && <p className="text-red-400 text-xs font-mono mb-3">[ERR] {error}</p>}

        <div className="flex gap-3 justify-end">
          <button onClick={onClose} className="min-h-[48px] px-5 tac-btn-muted">CANCEL</button>
          <button onClick={handleSubmit} disabled={saving}
            className="min-h-[48px] px-5 tac-btn disabled:opacity-50">
            {saving ? <Spinner size="sm" /> : isEdit ? 'SAVE' : 'CREATE'}
          </button>
        </div>
      </div>
    </div>
  );
}

// ── Password Reset Modal ────────────────────────────────────────────────────
function ResetPasswordModal({ target, onSave, onClose }) {
  const [pw, setPw] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  const handleSubmit = async () => {
    if (pw.length < 12) { setError('Password must be at least 12 characters.'); return; }
    if (!/[A-Za-z]/.test(pw) || !/[0-9]/.test(pw)) { setError('Must contain at least one letter and one digit.'); return; }
    setSaving(true);
    const ok = await onSave(pw, target.id);
    setSaving(false);
    if (ok) onClose();
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4">
      <div className="tac-panel shadow-tac-glow max-w-sm w-full p-6 animate-fade-in">
        <h2 className="text-lg font-bold text-tac-bright font-mono uppercase tracking-wide mb-2">Reset Password</h2>
        <p className="text-tac-muted text-sm mb-4">Set new access key for <strong className="text-tac-bright">{target.name}</strong>.</p>
        <input type="password" value={pw} onChange={(e) => { setPw(e.target.value); setError(''); }}
          placeholder="≥12 chars, letter + digit"
          className="w-full tac-input px-3 py-2 text-sm mb-3" />
        {error && <p className="text-red-400 text-xs font-mono mb-3">[ERR] {error}</p>}
        <div className="flex gap-3 justify-end">
          <button onClick={onClose} className="min-h-[48px] px-5 tac-btn-muted">CANCEL</button>
          <button onClick={handleSubmit} disabled={saving}
            className="min-h-[48px] px-5 tac-btn disabled:opacity-50">
            {saving ? <Spinner size="sm" /> : 'RESET'}
          </button>
        </div>
      </div>
    </div>
  );
}

// ── Main ────────────────────────────────────────────────────────────────────
export default function UserAccountsPage() {
  const navigate = useNavigate();
  const { user: me } = useAuth();
  const { toasts, showToast, dismissToast } = useToast();

  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showCreate, setShowCreate] = useState(false);
  const [editTarget, setEditTarget] = useState(null);
  const [resetTarget, setResetTarget] = useState(null);
  const [toggleTarget, setToggleTarget] = useState(null);
  const [deleteTarget, setDeleteTarget] = useState(null);

  const fetchUsers = useCallback(async () => {
    try {
      const res = await axios.get('/api/users');
      setUsers(res.data);
    } catch { showToast('Failed to load users', 'error'); }
    finally { setLoading(false); }
  }, [showToast]);

  useEffect(() => { fetchUsers(); }, [fetchUsers]);

  const handleCreate = async (form) => {
    try {
      const res = await axios.post('/api/users', form);
      setUsers((prev) => [res.data, ...prev]);
      showToast('User created', 'success');
      return true;
    } catch (err) {
      showToast(err.response?.data?.error || 'Create failed', 'error');
      return false;
    }
  };

  const handleEditRole = async (form, id) => {
    try {
      const res = await axios.patch(`/api/users/${id}`, { role: form.role });
      setUsers((prev) => prev.map((u) => u.id === id ? { ...u, ...res.data } : u));
      showToast('Role updated', 'success');
      return true;
    } catch (err) {
      showToast(err.response?.data?.error || 'Update failed', 'error');
      return false;
    }
  };

  const handleResetPassword = async (pw, id) => {
    try {
      await axios.patch(`/api/users/${id}`, { password: pw });
      showToast('Password reset', 'success');
      return true;
    } catch (err) {
      showToast(err.response?.data?.error || 'Reset failed', 'error');
      return false;
    }
  };

  const handleToggle = async () => {
    if (!toggleTarget) return;
    const newStatus = toggleTarget.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
    try {
      const res = await axios.patch(`/api/users/${toggleTarget.id}`, { status: newStatus });
      setUsers((prev) => prev.map((u) => u.id === toggleTarget.id ? { ...u, ...res.data } : u));
      showToast(`User ${newStatus.toLowerCase()}`, 'success');
    } catch (err) {
      showToast(err.response?.data?.error || 'Update failed', 'error');
    } finally { setToggleTarget(null); }
  };

  const handleDelete = async () => {
    if (!deleteTarget) return;
    try {
      await axios.delete(`/api/users/${deleteTarget.id}`);
      setUsers((prev) => prev.filter((u) => u.id !== deleteTarget.id));
      showToast('User deleted', 'success');
    } catch (err) {
      showToast(err.response?.data?.error || 'Delete failed', 'error');
    } finally { setDeleteTarget(null); }
  };

  return (
    <div className="min-h-screen bg-tac-darker text-tac-text p-4 md:p-6">
      <ToastContainer toasts={toasts} onDismiss={dismissToast} />
      {showCreate && <UserModal onSave={handleCreate} onClose={() => setShowCreate(false)} />}
      {editTarget && <UserModal target={editTarget} onSave={handleEditRole} onClose={() => setEditTarget(null)} />}
      {resetTarget && <ResetPasswordModal target={resetTarget} onSave={handleResetPassword} onClose={() => setResetTarget(null)} />}
      {toggleTarget && (
        <ConfirmModal
          title={toggleTarget.status === 'ACTIVE' ? 'Deactivate User' : 'Activate User'}
          message={`${toggleTarget.status === 'ACTIVE' ? 'Deactivate' : 'Activate'} ${toggleTarget.name}?`}
          confirmLabel={toggleTarget.status === 'ACTIVE' ? 'DEACTIVATE' : 'ACTIVATE'}
          danger={toggleTarget.status === 'ACTIVE'}
          onConfirm={handleToggle} onCancel={() => setToggleTarget(null)} />
      )}
      {deleteTarget && (
        <ConfirmModal
          title="Delete User"
          message={`Permanently delete "${deleteTarget.name}" (${deleteTarget.username})? This action is logged but cannot be undone.`}
          confirmLabel="DELETE" danger
          onConfirm={handleDelete} onCancel={() => setDeleteTarget(null)} />
      )}

      <header className="flex items-center justify-between mb-6">
        <div>
          <button onClick={() => navigate('/manager')} className="text-tac-muted hover:text-tac-green text-xs font-mono mb-1 block uppercase tracking-widest">← Back to Command Center</button>
          <h1 className="text-2xl font-black tracking-tight font-mono text-tac-bright">User Accounts</h1>
        </div>
        <button onClick={() => setShowCreate(true)}
          className="min-h-[48px] px-5 tac-btn">
          + ADD USER
        </button>
      </header>

      {loading ? (
        <div className="flex justify-center py-20"><Spinner /></div>
      ) : users.length === 0 ? (
        <EmptyState message="NO USER ACCOUNTS FOUND" />
      ) : (
        <div className="tac-panel overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-tac-border text-tac-muted text-[10px] font-mono uppercase tracking-[0.15em]">
                  <th className="text-left px-4 py-3">Name</th>
                  <th className="text-left px-4 py-3">Username</th>
                  <th className="text-center px-4 py-3">Role</th>
                  <th className="text-center px-4 py-3">Status</th>
                  <th className="text-right px-4 py-3">Actions</th>
                </tr>
              </thead>
              <tbody>
                {users.map((u) => {
                  const isMe = u.id === me?.id;
                  return (
                    <tr key={u.id} className="border-b border-tac-border/50 hover:bg-tac-mid/30">
                      <td className="px-4 py-3 font-medium text-tac-bright">
                        {u.name} {isMe && <span className="text-tac-muted text-xs font-mono ml-1">(you)</span>}
                      </td>
                      <td className="px-4 py-3 text-tac-muted font-mono">{u.username}</td>
                      <td className="px-4 py-3 text-center"><RoleBadge role={u.role} /></td>
                      <td className="px-4 py-3 text-center">
                        <span className={`text-xs font-mono font-bold px-2 py-0.5 rounded-tac border ${
                          (u.status ?? 'ACTIVE') === 'ACTIVE'
                            ? 'text-tac-green bg-tac-green/15 border-tac-green/30'
                            : 'text-red-400 bg-tac-red/15 border-tac-red/30'
                        }`}>
                          {u.status ?? 'ACTIVE'}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-right">
                        <div className="flex items-center justify-end gap-1.5">
                          <button onClick={() => setEditTarget(u)}
                            className="px-3 py-1.5 rounded-tac text-xs font-mono font-bold tac-btn-muted">ROLE</button>
                          <button onClick={() => setResetTarget(u)}
                            className="px-3 py-1.5 rounded-tac text-xs font-mono font-bold tac-btn-muted">RESET PW</button>
                          {!isMe && (
                            <button onClick={() => setToggleTarget(u)}
                              className={`px-3 py-1.5 rounded-tac text-xs font-mono font-bold transition border ${(u.status ?? 'ACTIVE') === 'ACTIVE' ? 'bg-tac-amber/15 text-amber-400 border-tac-amber/30 hover:bg-tac-amber/25' : 'bg-tac-green/15 text-tac-lime border-tac-green/30 hover:bg-tac-green/25'}`}>
                              {(u.status ?? 'ACTIVE') === 'ACTIVE' ? 'DEACTIVATE' : 'ACTIVATE'}
                            </button>
                          )}
                          {!isMe && u.role !== 'MANAGER' && (
                            <button onClick={() => setDeleteTarget(u)}
                              className="px-3 py-1.5 rounded-tac text-xs font-mono font-bold tac-btn-danger">DEL</button>
                          )}
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
