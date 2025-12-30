# Runtime-only image - expects JAR to be built by CI/CD
FROM eclipse-temurin:21-jre-alpine

# Install curl for healthcheck
RUN apk add --no-cache curl

WORKDIR /app

# Create a non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copy the pre-built JAR file from CI/CD
# Expects the JAR to be in target/ directory (built via: mvn clean package)
COPY target/*.jar app.jar

# Change ownership to the non-root user
RUN chown -R appuser:appgroup /app

# Switch to non-root user
USER appuser

# Expose the application port (Spring Boot default is 8080)
EXPOSE 8080

# Expose Netty TCP ports (configurable via environment variables)
EXPOSE 13001 13002 13003

# Set JVM options for containerized environment
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
