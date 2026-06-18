# -- Stage 1: Front-end build --------------------------------------------------
# Pinned to match the Node version declared in pom.xml's frontend-maven-plugin.
FROM node:24.11.1-alpine AS frontend
WORKDIR /app/front-end

RUN corepack enable && corepack prepare pnpm@9.15.0 --activate

# pnpm install layer - only busts when lockfile/manifest changes.
COPY front-end/package.json front-end/pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile

# Nx Cloud remote cache (no-op unless NX_CLOUD_ACCESS_TOKEN is set as a build arg).
ARG NX_CLOUD_ACCESS_TOKEN=""
ENV NX_CLOUD_ACCESS_TOKEN=$NX_CLOUD_ACCESS_TOKEN

# Front-end sources + build (consumes Nx Cloud cache if available).
COPY front-end/ ./
RUN pnpm run build

# -- Stage 2: Java build -------------------------------------------------------
FROM eclipse-temurin:25-jdk-alpine AS backend
WORKDIR /app

# Maven dependency layer - cached by pom.xml.
COPY mvnw mvnw.cmd pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw dependency:go-offline -q

# Java sources.
COPY src ./src

# Bring in the already-built front-end output as static resources.
COPY --from=frontend /app/src/main/resources/static ./src/main/resources/static

# Build the jar - front-end plugin disabled since we built it in stage 1.
RUN ./mvnw package -DskipTests -DskipFrontend=true -q

# -- Stage 3: Runtime ----------------------------------------------------------
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

COPY --from=backend /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
