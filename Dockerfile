# Multi-stage Dockerfile for LedgerX
# Build stage
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /workspace

# Cache dependencies
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline

# Copy source code and package application jar
COPY src/ src/
RUN ./mvnw -B clean package -DskipTests

# Runtime stage (minimal JRE)
FROM eclipse-temurin:21-jre
WORKDIR /app

# Run as non-root user (USER 10001)
RUN groupadd -g 10001 ledgerx && \
    useradd -u 10001 -g ledgerx -s /bin/sh ledgerx

COPY --from=builder --chown=ledgerx:ledgerx /workspace/target/ledgerx-0.1.0.jar app.jar

USER 10001

EXPOSE 8080

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
