# SOS Spring Boot Backend - Setup Guide

## Prerequisites

- **Java 17+** (verify: `java -version`)
- **Maven 3.8+** (verify: `mvn -version`) or use the included Maven wrapper (`./mvnw`)
- **PostgreSQL 14+** running on `localhost:5432`

## Quick Start

### 1. Database Setup

create the database if you havent already:

```bash
psql -U postgres -c "CREATE DATABASE sos_db;"
```

### 2. Configure Spring Boot

```bash
cp src/main/resources/application.properties.example src/main/resources/application.properties
```

open `application.properties` and put in your postgres username/password:

```properties
spring.datasource.username=${DB_USER:postgres}
spring.datasource.password=${DB_PASSWORD:yourpassword}
```

the JWT secret is pre-configured for development. change it for anything non-local.

### 3. Build & Run

```bash
./mvnw spring-boot:run
```

or build a JAR:

```bash
./mvnw clean package -DskipTests
java -jar target/support-of-sale-1.0.0.jar
```

the server starts on **port 3001** (HTTP) and **port 3002** (Socket.IO).

on first run with an empty database, it automatically creates the tables and seeds demo data (users, menu items, inventory, test orders). you should see the login credentials printed in the terminal:

| Role    | Username   | Password           |
|---------|------------|--------------------|
| MANAGER | manager    | Manager-Dev-2026   |
| BOH     | boh_cook   | BohCook-Dev-2026   |
| FOH     | foh_server | FohSvr-Dev-2026    |

### 4. Run the React Client

```bash
cd ../client
npm install
npm run dev
```

open http://localhost:5173 and log in with the credentials above.

## Architecture

```
server-spring/
  src/main/java/com/sos/
    config/         SecurityConfig, SocketIOConfig, JacksonConfig, DataSeeder, GlobalExceptionHandler
    controller/     REST endpoints: Auth, Order, Menu, Inventory, User, Alert, Sales
    dto/            Request/response DTOs with Jakarta Validation annotations
    model/          JPA entities (Hibernate auto-creates the tables)
    repository/     Spring Data JPA repositories
    security/       JWT filter, JwtUtil, JwtPrincipal, LoginRateLimiter
    service/        OrderService (business logic), SocketIOService (real-time), SecurityLogService
```

## Ports

| Service       | Port |
|---------------|------|
| Spring Boot   | 3001 |
| Socket.IO     | 3002 |
| React (Vite)  | 5173 |
| PostgreSQL    | 5432 |
