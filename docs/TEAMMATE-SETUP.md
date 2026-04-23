# Getting SOS Running on Your Machine

hey so heres how to get the app running locally. its not that bad, just follow the steps in order and you should be good.

## whats actually getting installed

SOS has three pieces:

1. **PostgreSQL** — the database. stores everything (users, orders, menu, etc.)
2. **Spring Boot** — the Java backend that runs the API. lives in `server-spring/`. it also creates the tables and seeds demo data automatically on first run
3. **React** — the frontend you see in the browser. lives in `client/`

you set them up in that order.

---

## stuff you need first

make sure you have all of these before starting. if you already have them just skip to Step 1.

### Java 17+

this is what the backend runs on. check if you have it:
```bash
java -version
```

if you see `openjdk version "17.x.x"` or higher you're good. if not grab it from [Adoptium](https://adoptium.net/) — pick the LTS version and just run through the installer.

**windows people:** theres a checkbox during install that says "Set JAVA_HOME variable" — check that or Maven is gonna yell at you later.

### Maven

maven is basically npm but for java. it reads `pom.xml` (like `package.json`), pulls down all the libraries, compiles the code, and runs the server. first time takes a while cause its downloading everything but after that its cached.

check if you have it:
```bash
mvn -version
```

if not dont worry about it — theres a built-in wrapper already in the project. just use `./mvnw` (mac/linux) or `mvnw.cmd` (windows) instead of `mvn` whenever you see it in the commands below.

### Node.js 18+

we need node for running the react frontend dev server.

```bash
node -v
```

if you dont have it go to [nodejs.org](https://nodejs.org/) and grab the LTS version.

### PostgreSQL 14+

this is the database. it runs in the background on your computer and the server connects to it.

```bash
psql --version
```

if you dont have it download from [postgresql.org/download](https://www.postgresql.org/download/). during install itll ask you to set a password for the `postgres` user — **remember this** youll need it in like 2 minutes.

after installing make sure its actually running:
- **Windows:** search "Services" in start menu, find "postgresql-x64-14" or whatever version, make sure it says Running
- **Mac:** if you used homebrew do `brew services start postgresql`

---

## Step 1: Create the Database

open a terminal and connect to postgres:

```bash
psql -U postgres
```

itll ask for the password you set during install. then run:

```sql
CREATE DATABASE sos_db;
```

type `\q` to get out.

if you get "psql: command not found" it means postgres isnt in your PATH. on windows its usually at `C:\Program Files\PostgreSQL\14\bin\` — either add that to your PATH or just type the full path.

---

## Step 2: Set Up Spring Boot

```bash
cd server-spring
```

copy the config template:
```bash
cp src/main/resources/application.properties.example src/main/resources/application.properties
```

open `server-spring/src/main/resources/application.properties` and find these lines:

```properties
spring.datasource.url=${DB_URL:jdbc:postgresql://localhost:5432/sos_db}
spring.datasource.username=${DB_USER:YOUR_PG_USER}
spring.datasource.password=${DB_PASSWORD:YOUR_PG_PASSWORD}
```

see the part after the colon? thats the default value. change `YOUR_PG_USER` to your postgres username (probably just `postgres`) and `YOUR_PG_PASSWORD` to your password. like:

```properties
spring.datasource.username=${DB_USER:postgres}
spring.datasource.password=${DB_PASSWORD:mypassword123}
```

dont touch anything else in there unless you know what youre doing. jwt secret and everything else is already set up for local dev.

**important:** this file has your local creds in it. its in `.gitignore` so it wont get pushed to github. dont rename it or take it out of gitignore.

---

## Step 3: Start the Server

still in `server-spring/`:

**mac/linux:**
```bash
./mvnw spring-boot:run
```

**windows (command prompt):**
```cmd
mvnw.cmd spring-boot:run
```

**windows (powershell):**
```powershell
.\mvnw.cmd spring-boot:run
```

**or if you installed maven globally:**
```bash
mvn spring-boot:run
```

first time its gonna download a ton of stuff — takes like 2-5 min depending on your wifi. just let it do its thing.

when its ready youll see something like:
```
Started SupportOfSaleApplication in X.XX seconds
Socket.IO server started on port 3002
```

if this is a fresh database itll also seed it with demo data (menu items, test users, test orders). you should see a table with the login credentials in the terminal output.

leave that terminal open. server is running on port 3001.

---

## Step 4: Start the Frontend

open a **new terminal** (keep the server running in the other one):

```bash
cd client
npm install
npm run dev
```

you should see:
```
VITE v5.x.x  ready in XXX ms

➜  Local:   http://localhost:5173/
```

open that in your browser.

---

## Step 5: Log In

use one of these test accounts (the seed script made them):

| Role | Username | Password |
|------|----------|----------|
| Manager | manager | Manager-Dev-2026 |
| Cook (BOH) | boh_cook | BohCook-Dev-2026 |
| Server (FOH) | foh_server | FohSvr-Dev-2026 |

each role gets a different dashboard so try logging in as different users to see everything.

---

## if something breaks

### "Port 3001 already in use"
something else is using that port. kill whatever it is:
- **Windows:** `netstat -ano | findstr :3001` to find the PID, then `taskkill /F /PID <the_pid>`
- **Mac/Linux:** `lsof -ti:3001 | xargs kill`

then try starting spring boot again.

### "Connection refused" or "password authentication failed"
your postgres creds in `application.properties` are wrong. check:
- is postgres actually running?
- username and password correct?
- database called `sos_db`? (run `psql -U postgres -l` to check)

### "relation does not exist" or "table not found"
make sure your `application.properties` has `spring.jpa.hibernate.ddl-auto=update` (not `validate`). that setting tells hibernate to create the tables automatically. if you changed it to validate by accident, switch it back and restart the server.

### Maven says "JAVA_HOME is not set"
java is installed but maven cant find it.
- **Windows:** search "Environment Variables" in start menu → "Edit the system environment variables" → add a new system variable called `JAVA_HOME` with the path to your java install (something like `C:\Program Files\Eclipse Adoptium\jdk-17.0.x-hotspot`)
- **Mac/Linux:** add `export JAVA_HOME=$(/usr/libexec/java_home)` to your `~/.bashrc` or `~/.zshrc` and restart your terminal

### "npm: command not found" on Windows
node isnt in your PATH. restart your terminal after installing node. if that doesnt work try command prompt instead of powershell.

### app loads but nothing shows / blank screen
open browser console (F12 → Console). if you see CORS or network errors the backend probably isnt running. check that the spring boot terminal says "Started" and doesnt have any red error stuff.

### I broke something and now it wont compile
if you accidentally messed up a java file you can reset it:
```bash
git checkout -- server-spring/
```
that undoes your local changes to that folder and puts it back to whatever the branch has.

---

## running tests

we have unit tests for the backend. to run them:

```bash
cd server-spring
./mvnw test
```

this runs all the JUnit tests and prints the results. you dont need the server running for these — they mock out the database and everything.

heres whats being tested right now:

**security stuff:**
- **JwtUtilTest** — tests the JWT token stuff. makes sure tokens get generated correctly, that the claims (user id, role, username) are in there, that tampered tokens get rejected, and that sessions expire after 12 hours.
- **LoginRateLimiterTest** — tests the login rate limiter. checks that you get blocked after too many attempts, that different IPs have separate limits, and that it doesnt crash on null IPs.
- **AuthControllerTest** — tests the login endpoint. covers successful login, wrong password, unknown username, inactive account, missing fields, and bad JSON. uses MockMvc so it tests the actual HTTP request/response without needing the full server running.

**orders:**
- **OrderServiceStatusTest** — tests order status transitions. makes sure valid transitions work (PENDING → IN_PROGRESS, PENDING → COMPLETED for checkout, etc.) and invalid ones throw errors (you cant go from COMPLETED back to PENDING). also checks that completing an order creates a sales record and sends socket events.
- **OrderServiceCreateTest** — tests order submission. checks that a valid order with menu items gets created correctly, that special instructions are saved, that ordering unavailable or nonexistent items fails, and that quantity defaults to 1 if you dont set it.

**menu:**
- **MenuControllerTest** — tests the menu CRUD endpoints. covers getting all items, creating a new item, catching duplicate names, rejecting negative prices, getting a single item by ID, deleting items, and 404s for nonexistent items.

**inventory:**
- **InventoryControllerTest** — tests inventory management. covers creating new items, catching duplicates, rejecting negative quantities, restocking (adds the restock amount), the low stock flag, and the cool part — when you restock something thats completely out, it automatically re-enables the linked menu items and sends socket events.

**alerts:**
- **AlertControllerTest** — tests the alert system. covers creating broadcast alerts, validating that SPECIFIC alerts need a target user, marking alerts as read (by the sender or manager), and making sure other users cant mark someone elses alerts as read (403).

**general:**
- **SosApplicationTests** — just checks that the Spring app context loads without errors. basically a sanity check that nothing is misconfigured.

you can always add more tests. just create a new file in `server-spring/src/test/java/com/sos/` following the same pattern as the ones above.

---



| What | Port | How to start |
|------|------|-------------|
| Spring Boot (API) | 3001 | `cd server-spring && ./mvnw spring-boot:run` |
| Socket.IO (real-time) | 3002 | starts automatically with spring boot |
| React (frontend) | 5173 | `cd client && npm run dev` |
| PostgreSQL (database) | 5432 | should already be running as a service |

---

## folder structure

```
Support-of-Sale/
├── client/          ← react frontend (what you see in the browser)
├── server-spring/   ← java spring boot backend (API + auto-seeds the DB)
│   └── src/main/resources/
│       ├── application.properties          ← YOUR local config (gitignored)
│       └── application.properties.example  ← template (safe to commit)
└── docs/            ← you are here
```

---

## tldr

1. install java 17+, node 18+, postgresql 14+
2. create `sos_db` database in postgres
3. `cd server-spring` → copy `application.properties.example` to `application.properties`, put in your postgres username/password
4. `./mvnw spring-boot:run` (leave it running — it creates the tables and seeds demo data on first run)
5. new terminal → `cd client && npm install && npm run dev`
6. go to http://localhost:5173 and log in as `manager` / `Manager-Dev-2026`

thats it. if something breaks check the troubleshooting section above or just message the group chat.
