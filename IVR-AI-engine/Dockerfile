# Use Maven and JDK 21 to build and run the application
FROM maven:3.9-eclipse-temurin-21

WORKDIR /app

# Copy the project files
COPY pom.xml .
COPY src ./src

# Install asterisk so the container has the CLI tool for add_extension.sh
RUN apt-get update && apt-get install -y asterisk && rm -rf /var/lib/apt/lists/*

# Cache dependencies
RUN mvn clean compile

# Expose Tomcat port (Cargo typically runs on 8080)
EXPOSE 8080

# Run the Tomcat server using the Cargo plugin (matches how it's run locally)
CMD ["mvn", "cargo:run"]
