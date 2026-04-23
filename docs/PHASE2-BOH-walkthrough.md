# SOS — Phase 2 BOH Walkthrough

Back of House (BOH) UI — fully implemented.

Phase 2 Part 1 builds the complete BOH Dashboard on top of the Phase 1 backend. Kitchen staff get a live two-panel interface to manage the order queue, control item availability, restock inventory, and broadcast alerts to FOH — all in real time over Socket.io.

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

Files added or changed in Phase 2 Part 1 are marked with `←`.

```
SE PROJECT/
├── client/
│   └── src/
│       ├── pages/
│       │   └── BOH/
│       │       └── Dashboard.jsx        ← Full BOH UI (all components)
│       └── components/
│           ├── RoleBadge.jsx            ← Role pill (navbar)
│           ├── StatusBadge.jsx          ← Coloured order status pill
│           ├── Spinner.jsx              ← Loading spinner for action buttons
│           ├── EmptyState.jsx           ← Empty queue / history placeholder
│           ├── ConfirmModal.jsx         ← Reusable confirm dialog
│           ├── useToast.js              ← Toast state hook
│           └── Toast.jsx                ← Toast notification UI
│
└── server/
    ├── prisma/
    │   └── seed.js                      ← Updated: adds 4 test orders
    └── src/
        └── routes/
            ├── orders.js                ← Updated: salesRecord included in responses
            └── inventory.js             ← Restock + auto-available logic (Phase 1)
```

---

## Prerequisites

- Phase 1 fully set up (database migrated, `.env` configured)
- Both `server/` and `client/` dependencies installed
- PostgreSQL running

---

## Setup — Phase 2

No new migrations or environment changes are needed. Just reseed to load the BOH test orders:

```bash
cd server
npm run db:seed
```

> This wipes all existing orders, alerts, and security logs before reseeding. Safe to run at any time during development.

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

Navigate to `http://localhost:5173` and log in as `boh_cook` / `boh123`.

---

## Seed Accounts

| Role    | Username     | Password     |
|---------|--------------|--------------|
| MANAGER | `manager`    | `manager123` |
| BOH     | `boh_cook`   | `boh123`     |
| FOH     | `foh_server` | `foh123`     |

> **Change these before any real deployment.**

---

## BOH Dashboard — Feature Walkthrough

### Layout

```
┌────────────────────────────────┬───────────────────────────┐
│  LEFT PANEL (60%)              │  RIGHT PANEL (40%)        │
│                                │                           │
│  [ Live Queue ] [ History ]    │  Item Availability        │
│                                │                           │
│  Order cards with actions      │  Broadcast Alert          │
│                                │                           │
└────────────────────────────────┴───────────────────────────┘
```

---

### Live Order Queue (left panel — default)

Fetches all active orders on mount and sorts them by priority: `PENDING → IN_PROGRESS → DELAYED`, oldest first within each group.

Each order card displays:
- Table number (large, prominent)
- Live elapsed time counter — updates every second
- Status badge
- All ordered items with quantities and special instructions
- Estimated wait badge (DELAYED orders only)
- Any BOH note already sent to FOH

**Actions per order:**

| Button   | Shown when                        | What it does                                                  |
|----------|-----------------------------------|---------------------------------------------------------------|
| Accept   | PENDING                           | Moves to IN_PROGRESS; triggers inventory decrement            |
| Complete | IN_PROGRESS or DELAYED            | Moves to COMPLETED; auto-creates SalesRecord                  |
| Delay    | PENDING or IN_PROGRESS            | Opens inline minute input; moves to DELAYED with wait time    |
| Note     | PENDING, IN_PROGRESS, or DELAYED  | Opens inline textarea; sends note to the FOH submitter        |
| Deny     | PENDING, IN_PROGRESS, or DELAYED  | Cancels the order                                             |

All buttons show a spinner while the request is in-flight and are disabled to prevent double-submission.

---

### Order History (left panel — History tab)

Lazy-loaded — only fetches from the server when the tab is first opened.

Displays `COMPLETED` and `CANCELLED` orders sorted newest first. Each card shows:
- Table number
- Timestamp (e.g. Mar 21, 02:45 PM)
- Status badge (green for COMPLETED, red for CANCELLED)
- **Total duration** — `completedAt − createdAt` shown as `Xm Ys total` (COMPLETED only)
- All items ordered
- BOH note if one was attached

---

### Item Availability Panel (right panel — top)

Lists every menu item grouped by category. Each row shows:

| Element | Description |
|---------|-------------|
| Item name | Truncated if long |
| Stock level | `X in stock`, `X in stock — low` (orange), or `OUT OF STOCK` (red) |
| Restock button | Adds the preset `restockAmount` in one click; re-enables item if it was out |
| Availability toggle | Manually flip between `AVAILABLE` (green) and `UNAVAILABLE` (red) |

Color coding on the row itself:
- Normal — dark gray background
- Low stock — orange-tinted background with orange text
- Out of stock — red-tinted background with red text and `OUT OF STOCK` label

---

### Broadcast Alert (right panel — bottom)

A 200-character textarea with a live character counter that turns red at 180+. Hitting **Broadcast to All FOH** sends the message to all FOH terminals over Socket.io. FOH staff see it as a dismissible banner at the top of their screen.

---

## Inventory Auto-Unavailable Logic

When BOH accepts an order (PENDING → IN_PROGRESS), the server:

1. Decrements each linked inventory item by the ordered quantity
2. If any item reaches **0**:
   - All linked menu items are set to `UNAVAILABLE` in the database
   - `item:unavailable` is emitted to BOH, FOH, and MANAGER rooms
3. BOH sees the toggle flip to red in real time without a page refresh
4. Hitting **Restock** adds `restockAmount` back, re-enables the menu item, and emits `item:available`

---

## Socket.io Events — BOH

| Event                | Direction       | Effect on BOH UI                                    |
|----------------------|-----------------|-----------------------------------------------------|
| `order:new`          | Server → BOH    | Prepends new card to the live queue                 |
| `order:statusChanged`| Server → BOH    | Updates card in place; removes it if COMPLETED      |
| `inventory:updated`  | Server → BOH    | Updates live stock numbers on the right panel       |
| `item:unavailable`   | Server → BOH    | Flips item toggle to UNAVAILABLE (red) in real time |
| `item:available`     | Server → BOH    | Flips item toggle to AVAILABLE (green) in real time |

---

## API Endpoints Used by BOH Dashboard

| Method | Endpoint                      | Description                            |
|--------|-------------------------------|----------------------------------------|
| GET    | `/api/orders`                 | Load active queue on mount             |
| GET    | `/api/orders?status=COMPLETED`| Load history (lazy, on tab open)       |
| GET    | `/api/orders?status=CANCELLED`| Load history (lazy, on tab open)       |
| PATCH  | `/api/orders/:id/status`      | Accept / delay / complete / deny order |
| PATCH  | `/api/orders/:id/note`        | Send note to FOH submitter             |
| GET    | `/api/menu`                   | Load item availability panel           |
| PATCH  | `/api/menu/:id/availability`  | Toggle item available/unavailable      |
| POST   | `/api/inventory/:id/restock`  | Restock an inventory item              |
| POST   | `/api/alerts`                 | Broadcast alert to all FOH             |

---

## Test Scenarios

### Seed state on fresh `npm run db:seed`

| Table | Status      | Items                                                                  | Note                                       |
|-------|-------------|------------------------------------------------------------------------|--------------------------------------------|
| 3     | PENDING     | 1× Classic Burger, **2× French Fries**, 1× Soft Drink                 | Accepting this drains Fries stock to 0     |
| 7     | PENDING     | 1× Chicken Wings, 1× Mozzarella Sticks *(extra marinara)*, 3× Draft Beer | —                                       |
| 1     | IN_PROGRESS | 1× BBQ Bacon Burger, 1× Grilled Chicken Sandwich, 1× House Salad *(no croutons)* | —                              |
| 5     | DELAYED     | 2× BBQ Bacon Burger, 2× French Fries, 2× Chocolate Lava Cake          | ~10 min wait, note pre-set                 |

French Fries seeds at **qty = 2** with a threshold of 5, so the right panel shows it as **low stock (orange)** on first load.

### Step-by-step test flows

| What to test | Steps |
|--------------|-------|
| **Accept order** | Hit Accept on Table 3 → card flips to IN_PROGRESS |
| **Inventory drains to 0** | Accept Table 3 → French Fries hits 0 → right panel flips to UNAVAILABLE (red) in real time |
| **Restock** | Hit +20 on French Fries → quantity restores, toggle flips back to AVAILABLE (green) |
| **Delay order** | Hit Delay on Table 7 → enter minutes → card updates with orange DELAYED badge and wait time |
| **Complete order** | Hit Complete on Table 1 → card removed from queue |
| **View history with duration** | Click History tab → Table 1 shows green COMPLETED badge and `Xm Ys total` |
| **Deny order** | Hit Deny on any active order → removed from queue, appears in history as CANCELLED (no duration) |
| **Send note** | Hit Note on Table 7 → type message → toast confirms, note stored on order |
| **Broadcast alert** | Type in the Broadcast textarea → hit button → all FOH terminals receive it as a banner |
| **Manual toggle** | Click any AVAILABLE/UNAVAILABLE button on the right panel to manually flip an item |

---

## Useful Commands

```bash
# Server
npm run dev          # Dev server with nodemon
npm run db:seed      # Reset and reseed all test data
npm run db:studio    # Open Prisma Studio (visual DB browser)
npm run db:reset     # Full schema reset + reseed (destructive)

# Client
npm run dev          # Dev server with HMR
npm run build        # Production build → dist/
```

---

## What Was Delivered in Phase 2 Part 1

- [x] Live order queue with real-time push (Socket.io)
- [x] Per-order actions: Accept, Delay, Complete, Deny, Note
- [x] Inline delay-minute input and note textarea (no modals)
- [x] Live elapsed time counter on every active order card
- [x] Lazy-loaded order history with completion duration
- [x] Item availability panel grouped by category with live stock levels
- [x] Low stock and out-of-stock visual states
- [x] One-click restock button per item
- [x] Real-time inventory updates via Socket.io
- [x] Alert broadcaster to all FOH staff
- [x] Toast notification system for all actions
- [x] Spinner + disabled state on all buttons during requests

## What's Next (Phase 2 — Remaining)

- [ ] FOH: Menu browser with category filtering and unavailable item greying
- [ ] FOH: Order submission form with table number and special instructions
- [ ] FOH: Live order status tracker (acknowledged / delayed / completed notifications)
- [ ] Manager: Inventory table with low-stock highlighted rows
- [ ] Manager: Date-range sales report with CSV export
- [ ] Manager: User account management table
- [ ] Manager: Security log viewer with filters
