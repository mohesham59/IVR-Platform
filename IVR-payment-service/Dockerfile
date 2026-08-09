# Use Maven and JDK 21 to build and run the application
FROM maven:3.9-eclipse-temurin-21

WORKDIR /app

# Install curl for container healthcheck
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# Copy project files
COPY pom.xml .
COPY src ./src

# Cache dependencies and build WAR package
RUN mvn clean package -DskipTests

# Expose default Payment Service port
EXPOSE 8082

# Run Tomcat server using Cargo Maven plugin
CMD ["mvn", "cargo:run"]
