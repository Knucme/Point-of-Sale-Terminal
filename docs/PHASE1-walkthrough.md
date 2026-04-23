# SOS — Phase 1 Walkthrough

A real-time restaurant Point of Sale platform unifying Back of House (BOH), Front of House (FOH), and Manager workflows through a shared networked system.

Phase 1 covers the full backend, authentication layer, real-time infrastructure, and client scaffolding. No role-specific UI was built in this phase.

---

## Tech Stack

| Layer      | Technology                          |
|------------|-------------------------------------|
| Frontend   | React 18 + Vite 5 + TailwindCSS 3  |
| Backend    | Node.js + Express 4                 |
| Database   | PostgreSQL + Prisma ORM             |
| Real-time  | Socket.io (WebSockets)              |
| Auth       | JWT + bcrypt                        |

---

## Project Structure

```
SE PROJECT/
├── client/                  # React + Vite frontend
│   ├── src/
│   │   ├── context/         # AuthContext (JWT, session, inactivity timer)
│   │   ├── pages/
│   │   │   ├── Login.jsx
│   │   │   ├── BOH/         # Back of House dashboard (placeholder)
│   │   │   ├── FOH/         # Front of House dashboard (placeholder)
│   │   │   └── Manager/     # Manager analytics dashboard (placeholder)
│   │   ├── App.jsx          # Routes + role guards
│   │   ├── socket.js        # Socket.io client singleton
│   │   └── index.css        # Tailwind + global styles
│   ├── vite.config.js
│   └── tailwind.config.js
│
└── server/                  # Express backend
    ├── prisma/
    │   ├── schema.prisma    # Full database schema
    │   └── seed.js          # Seed: 3 accounts, 10 menu items, 10 inventory rows
    └── src/
        ├── index.js         # HTTP server entry point
        ├── app.js           # Express app + routes
        ├── socket.js        # Socket.io server + event handlers
        ├── middleware/
        │   ├── auth.js      # JWT verification middleware
        │   └── rbac.js      # Role-based access control guards
        ├── routes/
        │   ├── auth.js      # POST /login, POST /refresh, GET /me
        │   ├── orders.js    # Order submission + status transitions
        │   ├── menu.js      # Menu CRUD + availability toggle
        │   ├── inventory.js # Inventory management (Manager only)
        │   ├── alerts.js    # BOH alert system
        │   ├── sales.js     # Sales records + summary
        │   └── users.js     # User management + security logs
        └── utils/
            └── securityLog.js  # Security event logging helper
```

---

## Prerequisites

- **Node.js** v18+ ([nodejs.org](https://nodejs.org))
- **PostgreSQL** v14+ running locally or remotely
- **npm** v9+

---

## Setup — First Time

### 1. Clone / open the project

```bash
cd "SE PROJECT"
```

### 2. Configure the server environment

```bash
cd server
cp .env.example .env
```

Edit `server/.env`:

```env
DATABASE_URL="postgresql://YOUR_USER:YOUR_PASSWORD@localhost:5432/sos_db"
JWT_SECRET="replace-with-a-long-random-string"
JWT_EXPIRES_IN="10m"
PORT=3001
CLIENT_URL="http://localhost:5173"
```

> **Generate a secure JWT secret:**
> ```bash
> node -e "console.log(require('crypto').randomBytes(64).toString('hex'))"
> ```

### 3. Install server dependencies

```bash
# still inside /server
npm install
```

### 4. Create the database and run migrations

Make sure PostgreSQL is running and the database exists:

```sql
-- In psql:
CREATE DATABASE sos_db;
```

Then:

```bash
npm run db:generate   # Generate Prisma client
npm run db:migrate    # Apply schema migrations
npm run db:seed       # Populate seed data
```

### 5. Install client dependencies

```bash
cd ../client
npm install
```

---

## Running in Development

Open **two terminals**:

**Terminal 1 — Backend:**
```bash
cd server
npm run dev
# Starts on http://localhost:3001
```

**Terminal 2 — Frontend:**
```bash
cd client
npm run dev
# Starts on http://localhost:5173
```

Navigate to `http://localhost:5173` in your browser.

---

## Seed Accounts

| Role    | Username     | Password     |
|---------|--------------|--------------|
| MANAGER | `manager`    | `manager123` |
| BOH     | `boh_cook`   | `boh123`     |
| FOH     | `foh_server` | `foh123`     |

> **Change these before any real deployment.**

---

## API Reference

### Auth
| Method | Endpoint            | Auth | Role | Description            |
|--------|---------------------|------|------|------------------------|
| POST   | `/api/auth/login`   | No   | Any  | Login, returns JWT     |
| POST   | `/api/auth/refresh` | JWT  | Any  | Silently refresh token |
| GET    | `/api/auth/me`      | JWT  | Any  | Validate session       |

### Orders
| Method | Endpoint                 | Role         | Description                       |
|--------|--------------------------|--------------|-----------------------------------|
| GET    | `/api/orders`            | BOH, MANAGER | All orders (filterable by status) |
| GET    | `/api/orders/my`         | FOH          | Caller's own orders               |
| POST   | `/api/orders`            | FOH          | Submit new order                  |
| PATCH  | `/api/orders/:id/status` | BOH, MANAGER | Advance order status              |
| PATCH  | `/api/orders/:id/note`   | BOH, MANAGER | Attach BOH note to order          |

### Menu
| Method | Endpoint                     | Role         | Description               |
|--------|------------------------------|--------------|---------------------------|
| GET    | `/api/menu`                  | All          | Full menu list            |
| PATCH  | `/api/menu/:id/availability` | BOH, MANAGER | Mark available/unavailable|
| POST   | `/api/menu`                  | MANAGER      | Create menu item          |
| PUT    | `/api/menu/:id`              | MANAGER      | Update menu item          |
| DELETE | `/api/menu/:id`              | MANAGER      | Delete menu item          |

### Inventory
| Method | Endpoint                    | Role         | Description              |
|--------|-----------------------------|--------------|--------------------------|
| GET    | `/api/inventory`            | MANAGER      | All inventory + flags    |
| POST   | `/api/inventory`            | MANAGER      | Create inventory item    |
| PATCH  | `/api/inventory/:id`        | MANAGER      | Update quantity/threshold|
| POST   | `/api/inventory/:id/restock`| BOH, MANAGER | Add restockAmount to qty |

### Alerts
| Method | Endpoint               | Role         | Description         |
|--------|------------------------|--------------|---------------------|
| POST   | `/api/alerts`          | BOH          | Send alert          |
| GET    | `/api/alerts`          | BOH, MANAGER | View sent alerts    |
| PATCH  | `/api/alerts/:id/read` | All          | Mark alert as read  |

### Sales
| Method | Endpoint             | Role    | Description                   |
|--------|----------------------|---------|-------------------------------|
| GET    | `/api/sales`         | MANAGER | All records (date range opt.) |
| GET    | `/api/sales/summary` | MANAGER | Live dashboard summary        |

### Users
| Method | Endpoint                    | Role    | Description              |
|--------|-----------------------------|---------|--------------------------|
| GET    | `/api/users`                | MANAGER | All accounts             |
| POST   | `/api/users`                | MANAGER | Create account           |
| PATCH  | `/api/users/:id`            | MANAGER | Update role/status       |
| DELETE | `/api/users/:id`            | MANAGER | Delete account           |
| GET    | `/api/users/security-logs`  | MANAGER | Paginated security log   |

---

## Socket.io Events

| Event                   | Direction         | Payload                             |
|-------------------------|-------------------|-------------------------------------|
| `order:new`             | Server → BOH      | Full order object                   |
| `order:acknowledged`    | Server → FOH      | `{ order }`                         |
| `order:delayed`         | Server → FOH      | `{ order, estimatedWait }`          |
| `order:cancelled`       | Server → FOH      | `{ order }`                         |
| `order:note`            | Server → FOH user | `{ orderId, tableNumber, note }`    |
| `order:statusChanged`   | Server → MANAGER  | Full order object                   |
| `item:unavailable`      | Server → All      | `{ menuItemId, name }`              |
| `item:available`        | Server → All      | `{ menuItemId, name }`              |
| `alert:broadcast`       | Server → FOH      | Alert object                        |
| `inventory:updated`     | Server → BOH      | Array of updated inventory rows     |
| `terminal:disconnected` | Server → MANAGER  | `{ userId, name, role, timestamp }` |

All Socket.io connections require a valid JWT passed as `socket.handshake.auth.token`.

---

## Security

- Passwords hashed with **bcrypt** (12 salt rounds) — never stored or transmitted in plaintext
- All protected routes require a valid JWT `Authorization: Bearer <token>` header
- Every RBAC denial is logged to the `SecurityLog` table
- Login failures (wrong password, unknown user, inactive account) are logged
- Session auto-expires after **10 minutes of inactivity** (client-side timer)
- JWT is silently refreshed every 8 minutes while the user is active

---

## Useful Commands

```bash
# Server
npm run dev          # Dev server with nodemon
npm run db:studio    # Open Prisma Studio (visual DB editor)
npm run db:reset     # Reset DB and re-seed (destructive!)

# Client
npm run dev          # Dev server with HMR
npm run build        # Production build → dist/
```

---

## What Was Delivered in Phase 1

- [x] PostgreSQL schema — 8 models, 5 enums
- [x] JWT authentication with silent refresh
- [x] 10-minute inactivity auto-logout (client-side)
- [x] RBAC middleware — BOH, FOH, MANAGER enforced on every route
- [x] RBAC denial logging to SecurityLog
- [x] Socket.io server with JWT handshake and role rooms
- [x] All REST API routes (auth, orders, menu, inventory, alerts, sales, users)
- [x] Inventory auto-decrement on order acceptance
- [x] Auto-unavailable when stock hits 0, auto-available on restock
- [x] SalesRecord auto-created on order completion
- [x] React client scaffold with Login page and role-guarded route tree
- [x] AuthContext — login, logout, session restore, inactivity timer

## What's Next (Phase 2 — UI)

- [ ] BOH: Full order queue with acknowledge / delay / complete controls
- [ ] BOH: Item availability toggle panel
- [ ] BOH: Alert composer (broadcast + targeted)
- [ ] FOH: Menu browser with category filtering
- [ ] FOH: Order submission form with special instructions
- [ ] FOH: Live order status tracker per table
- [ ] Manager: Inventory table with low-stock highlights
- [ ] Manager: Date-range report export (CSV)
- [ ] Manager: User management modal
- [ ] Manager: Security log viewer with filters
