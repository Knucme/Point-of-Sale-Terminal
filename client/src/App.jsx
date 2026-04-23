import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider, useAuth } from './context/AuthContext';
import Login from './pages/Login';
import BOHDashboard     from './pages/BOH/Dashboard';
import FOHDashboard     from './pages/FOH/Dashboard';
import ManagerDashboard from './pages/Manager/Dashboard';
import InventoryPage    from './pages/Manager/Inventory';
import SalesReportPage  from './pages/Manager/SalesReport';
import UserAccountsPage from './pages/Manager/UserAccounts';
import SecurityLogsPage from './pages/Manager/SecurityLogs';

// ── Role-guarded route wrapper ───────────────────────────────────────────────
const RoleRoute = ({ allowedRole, children }) => {
  const { user, loading } = useAuth();

  if (loading) {
    return (
      <div className="flex items-center justify-center h-screen bg-tac-black">
        <div className="text-tac-green text-sm font-mono animate-pulse-green uppercase tracking-widest">Authenticating...</div>
      </div>
    );
  }

  if (!user) return <Navigate to="/login" replace />;

  if (user.role !== allowedRole) {
    return <Navigate to={`/${user.role.toLowerCase()}`} replace />;
  }

  return children;
};

// ── Route tree ───────────────────────────────────────────────────────────────
const AppRoutes = () => {
  const { user, loading } = useAuth();

  if (loading) {
    return (
      <div className="flex items-center justify-center h-screen bg-tac-black">
        <div className="text-center">
          <div className="inline-block border border-tac-green/30 px-5 py-2 mb-3">
            <h1 className="text-3xl font-black text-tac-green font-mono tracking-[0.2em]">SOS</h1>
          </div>
          <p className="text-tac-muted font-mono text-xs animate-pulse-green uppercase tracking-widest">Initializing terminal<span className="animate-blink">_</span></p>
        </div>
      </div>
    );
  }

  return (
    <Routes>
      {/* Login — redirect to dashboard if already authenticated */}
      <Route
        path="/login"
        element={user ? <Navigate to={`/${user.role.toLowerCase()}`} replace /> : <Login />}
      />

      {/* Role-specific dashboards */}
      <Route path="/boh"     element={<RoleRoute allowedRole="BOH"><BOHDashboard /></RoleRoute>} />
      <Route path="/foh"     element={<RoleRoute allowedRole="FOH"><FOHDashboard /></RoleRoute>} />
      <Route path="/manager" element={<RoleRoute allowedRole="MANAGER"><ManagerDashboard /></RoleRoute>} />

      {/* Manager sub-pages */}
      <Route path="/manager/inventory"     element={<RoleRoute allowedRole="MANAGER"><InventoryPage /></RoleRoute>} />
      <Route path="/manager/sales"         element={<RoleRoute allowedRole="MANAGER"><SalesReportPage /></RoleRoute>} />
      <Route path="/manager/users"         element={<RoleRoute allowedRole="MANAGER"><UserAccountsPage /></RoleRoute>} />
      <Route path="/manager/security-logs" element={<RoleRoute allowedRole="MANAGER"><SecurityLogsPage /></RoleRoute>} />

      {/* Catch-all */}
      <Route path="*" element={<Navigate to="/login" replace />} />
    </Routes>
  );
};

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <AppRoutes />
      </AuthProvider>
    </BrowserRouter>
  );
}
