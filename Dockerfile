# ─── Stage 1: Build React frontend ───────────────────────────────────────────
FROM node:20-alpine AS frontend
WORKDIR /app/client
COPY client/package*.json ./
RUN npm ci
COPY client/ ./
RUN npm run build

# ─── Stage 2: Build Spring Boot JAR ─────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17 AS backend
WORKDIR /app

# Copy pom.xml first (caches dependency layer)
COPY server-spring/pom.xml ./pom.xml
COPY server-spring/.mvn ./.mvn
COPY server-spring/mvnw ./mvnw
RUN chmod +x mvnw && ./mvnw dependency:go-offline -q

# Copy React build into Spring Boot static resources
COPY --from=frontend /app/client/dist ./src/main/resources/static/

# Copy source and build JAR
COPY server-spring/src ./src
RUN ./mvnw clean package -DskipTests -q

# ─── Stage 3: Runtime ───────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=backend /app/target/support-of-sale-1.0.0.jar app.jar

EXPOSE 3001 3002
CMD ["java", "-jar", "app.jar"]
