# ==============================================================================
# ROOT DOCKERFILE FOR RENDER BACKEND WEB SERVICE
# ==============================================================================
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder
WORKDIR /app

# Copy backend pom and source code
COPY velocura-backend/pom.xml .
COPY velocura-backend/src ./src

# Build production JAR without running tests during build
RUN mvn clean package -DskipTests -B

# Stage 2: Runtime Environment
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copy compiled executable jar
COPY --from=builder /app/target/*.jar app.jar

# Expose HTTP ports for Render dynamic port binding
EXPOSE 8080 10000

ENV PORT=8080

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
