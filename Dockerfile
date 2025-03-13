FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /app
COPY . .

# Create .m2 directory for settings
RUN mkdir -p /root/.m2

# Create settings.xml with GitHub authentication
RUN echo '<?xml version="1.0" encoding="UTF-8"?>' > /root/.m2/settings.xml && \
    echo '<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"' >> /root/.m2/settings.xml && \
    echo '          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"' >> /root/.m2/settings.xml && \
    echo '          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 https://maven.apache.org/xsd/settings-1.0.0.xsd">' >> /root/.m2/settings.xml && \
    echo '    <servers>' >> /root/.m2/settings.xml && \
    echo '        <server>' >> /root/.m2/settings.xml && \
    echo '            <id>github</id>' >> /root/.m2/settings.xml && \
    echo '            <username>ssd2658</username>' >> /root/.m2/settings.xml && \
    echo '            <password>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</password>' >> /root/.m2/settings.xml && \
    echo '        </server>' >> /root/.m2/settings.xml && \
    echo '    </servers>' >> /root/.m2/settings.xml && \
    echo '</settings>' >> /root/.m2/settings.xml

RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre-jammy

WORKDIR /app
COPY --from=builder /app/portfolio-service/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
