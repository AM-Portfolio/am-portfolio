# Multi-stage build for Portfolio Service
# Stage 1: Build with Maven
ARG BASE_REGISTRY=""
FROM ${BASE_REGISTRY}am-java-maven-base:latest AS build

# Build arguments for GitHub authentication (needed for private dependencies)
ARG GITHUB_PACKAGES_USERNAME
ARG GITHUB_PACKAGES_TOKEN

WORKDIR /build

# Copy the entire project for multi-module build
COPY . .

# Build the application
# We need to pass the GitHub credentials to Maven so it can pull private dependencies
RUN GITHUB_PACKAGES_USERNAME=${GITHUB_PACKAGES_USERNAME} GITHUB_PACKAGES_TOKEN=${GITHUB_PACKAGES_TOKEN} \
    mvn clean package -DskipTests -B

# Stage 2: Runtime with JRE 21
FROM eclipse-temurin:21-jdk-jammy

WORKDIR /app

# Copy the built JAR from build stage
COPY --from=build /build/portfolio-app/target/*.jar app.jar

# Install curl for healthcheck
RUN apt-get update && \
    apt-get install -y curl && \
    rm -rf /var/lib/apt/lists/* && \
    # Set timezone
    ln -sf /usr/share/zoneinfo/Asia/Kolkata /etc/localtime

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
