# syntax=docker/dockerfile:1.4
# Multi-stage Dockerfile with .m2 cache mount support for fast compilation
FROM eclipse-temurin:21-jdk-jammy

WORKDIR /app

# Create a non-root user/group matching the Kubernetes securityContext (UID/GID 1000).
RUN groupadd -g 1000 spring && \
    useradd -u 1000 -g spring -s /bin/sh -m spring && \
    mkdir -p /var/log/am-portfolio && \
    chown -R spring:spring /app /var/log/am-portfolio

# Install curl for healthcheck (as root, before switching user)
RUN apt-get update && \
    apt-get install -y curl && \
    rm -rf /var/lib/apt/lists/* && \
    # Set timezone
    ln -sf /usr/share/zoneinfo/Asia/Kolkata /etc/localtime

# Copy the locally pre-built JAR from host .m2 target
COPY --chown=spring:spring portfolio-app/target/*.jar app.jar

# Drop to non-root — matches K8s securityContext runAsUser: 1000
USER 1000:1000

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
