# Multi-stage build for Portfolio Service (Unified Monorepo)
# Stage 1: Build with Maven
ARG BASE_REGISTRY=""
FROM maven:3.9.6-eclipse-temurin-21 AS build

# Build arguments for GitHub authentication
ARG GITHUB_PACKAGES_USERNAME
ARG GITHUB_PACKAGES_TOKEN

WORKDIR /build

# Copy settings.xml to Maven config directory
COPY settings.xml /root/.m2/settings.xml

# Copy the entire project for unified build
COPY . .

# Build everything in one go.
# Secrets are preferred so the package token is not printed as a build-arg.
# ARG remains the fallback for CI that still passes --build-arg.
RUN --mount=type=cache,target=/root/.m2 \
    --mount=type=secret,id=github_token,required=false \
    --mount=type=secret,id=github_actor,required=false \
    GITHUB_ACTOR="$(if [ -f /run/secrets/github_actor ]; then cat /run/secrets/github_actor; else printf '%s' "$GITHUB_PACKAGES_USERNAME"; fi)" \
    GITHUB_TOKEN="$(if [ -f /run/secrets/github_token ]; then cat /run/secrets/github_token; else printf '%s' "$GITHUB_PACKAGES_TOKEN"; fi)" \
    mvn clean package -DskipTests -B -s settings.xml -U

# Stage 2: Runtime with JRE 21
FROM eclipse-temurin:21-jdk-jammy

WORKDIR /app

# Create a non-root user/group matching the Kubernetes securityContext (UID/GID 1001).
# Without this the container runs as root, which violates the pod's
# runAsNonRoot: true + runAsUser: 1001 + fsGroup: 1001 security policy.
RUN groupadd -g 1001 spring && \
    useradd -u 1001 -g spring -s /bin/sh -m spring && \
    mkdir -p /var/log/am-portfolio && \
    chown -R spring:spring /app /var/log/am-portfolio

# Install curl for healthcheck (as root, before switching user)
RUN apt-get update && \
    apt-get install -y curl && \
    rm -rf /var/lib/apt/lists/* && \
    # Set timezone
    ln -sf /usr/share/zoneinfo/Asia/Kolkata /etc/localtime

# Copy the built JAR from build stage and set correct ownership
COPY --from=build --chown=spring:spring /build/portfolio-app/target/*.jar app.jar

# Drop to non-root — matches K8s securityContext runAsUser: 1001
USER 1001:1001

# Set environment variables
ENV SPRING_PROFILES_ACTIVE=docker
ENV TZ=Asia/Kolkata

# Expose the application port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
