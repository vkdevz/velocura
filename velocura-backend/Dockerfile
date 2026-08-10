# ==============================================================================
# STAGE 1: BUILD STAGE
# ==============================================================================
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder
WORKDIR /app

# Copy source directory and compile the application jar
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests -B

# ==============================================================================
# STAGE 2: RUNTIME RUN STAGE
# ==============================================================================
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copy the compiled executable jar from builder stage
COPY --from=builder /app/target/*.jar app.jar

# Expose HTTP REST API ports
EXPOSE 8080 10000

ENV PORT=8080

# Configure execution entrypoint with safe RAM limits for Render / container environments
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
