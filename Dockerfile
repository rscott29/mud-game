# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /app

# Copy Maven wrapper and pom first (layer cache for dependencies)
COPY mvnw mvnw.cmd pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw dependency:go-offline -q

# Copy source and build
COPY src ./src
COPY front-end ./front-end
RUN ./mvnw package -DskipTests -q

# ── Stage 2: Runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
