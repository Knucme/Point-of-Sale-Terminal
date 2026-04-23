# SOS — Support of Sale

A real-time restaurant Point of Sale platform unifying Back of House (BOH), Front of House (FOH), and Manager workflows through a shared networked system.

---

## Documentation

All phase walkthroughs are in the `docs/` folder.

| Document                                                      | Description                                                     |
| ------------------------------------------------------------- | --------------------------------------------------------------- |
| [TEAMMATE-SETUP.md](./docs/TEAMMATE-SETUP.md)                 | **Start here** — step-by-step guide to run the app from scratch |
| [PHASE1-walkthrough.md](./docs/PHASE1-walkthrough.md)         | Backend, auth, RBAC, Socket.io, full API reference, setup guide |
| [PHASE2-BOH-walkthrough.md](./docs/PHASE2-BOH-walkthrough.md) | BOH Dashboard — order queue, inventory, alerts, test scenarios  |
| [server-spring/SETUP.md](./server-spring/SETUP.md)            | Java/Spring Boot backend — setup, architecture, port reference  |

---

## Backend (Java / Spring Boot)

So the backend had to be rewrote from Node/Express into Java + Spring Boot for SE370. The React frontend didn't change at all — the Java server hits the same endpoints, same socket events, same database. Basically a drop-in swap.

### How to run it

```bash
# 1. Create the database (one time thing)
psql -U postgres -c "CREATE DATABASE sos_db;"

# 2. Start the Spring Boot server (it creates the tables + seeds demo data automatically)
cd server-spring
cp src/main/resources/application.properties.example src/main/resources/application.properties
# ^^^ open that file and put in your postgres username/password
./mvnw spring-boot:run        # runs on :3001 (HTTP) + :3002 (Socket.IO)

# 3. Start the React client
cd ../client
npm install && npm run dev    # opens on :5173
```

If you're setting this up for the first time, check out [TEAMMATE-SETUP.md](./docs/TEAMMATE-SETUP.md) — it walks you through everything from installing Java to getting the app running.

### What we're using

| What          | Tech                                                                |
| ------------- | ------------------------------------------------------------------- |
| HTTP stuff    | Spring Web MVC                                                      |
| Database      | Spring Data JPA / Hibernate 6                                       |
| Auth / tokens | JJWT 0.12.5 + Spring Security                                       |
| Passwords     | BCrypt (works with the Node bcrypt hashes so old logins still work) |
| WebSockets    | netty-socketio 2.0.9 (runs on port 3002)                            |
| PDF exports   | OpenPDF 2.0.2                                                       |

More details in [`server-spring/SETUP.md`](./server-spring/SETUP.md) if you need them.

---

## What changed recently

Just so everyone's on the same page:

- We moved the whole backend to Java/Spring Boot. Everything runs through `server-spring/` now — database seeding, API, all of it.
- Login rate limiting is in — you get 10 tries per 5 min then it locks you out.
- Sessions expire after 12 hours, so you'll have to log back in after that.
- FOH can only see their own orders now, and alerts can only be cleared by whoever sent them (or manager).
- Passwords have to be at least 12 chars with a letter and a number.
- We're logging security stuff — logins, cancellations, menu/inventory changes, etc.
- Request size is capped at 1MB so nobody sends something crazy.
