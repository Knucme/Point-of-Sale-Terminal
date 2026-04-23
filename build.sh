#!/usr/bin/env bash
# ─── Production Build Script ────────────────────────────────────────────────
# Builds the React frontend, copies it into Spring Boot's static resources,
# then builds the Spring Boot fat JAR. Used by Render's build command.
# ─────────────────────────────────────────────────────────────────────────────

set -e

echo "══════════════════════════════════════════════════════"
echo "  SOS — Support of Sale — Production Build"
echo "══════════════════════════════════════════════════════"

# 1. Build React frontend
echo ""
echo "▸ Building React frontend..."
cd client
npm install
npm run build
cd ..

# 2. Copy React build output into Spring Boot static resources
echo ""
echo "▸ Copying frontend into Spring Boot static resources..."
STATIC_DIR="server-spring/src/main/resources/static"
rm -rf "$STATIC_DIR"
cp -r client/dist "$STATIC_DIR"

# 3. Build Spring Boot JAR
echo ""
echo "▸ Building Spring Boot JAR..."
cd server-spring
./mvnw clean package -DskipTests -q
cd ..

echo ""
echo "══════════════════════════════════════════════════════"
echo "  ✓ Build complete!"
echo "  JAR: server-spring/target/support-of-sale-1.0.0.jar"
echo "══════════════════════════════════════════════════════"
