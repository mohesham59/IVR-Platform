# Use Maven and JDK 21 to build and run the application
FROM maven:3.9-eclipse-temurin-21

WORKDIR /app

# Copy the project files
COPY pom.xml .
# Install asterisk so the container has the CLI tool for add_extension.sh,
# plus python3/gTTS/ffmpeg so voice-prompt generation subprocesses work in-container.
RUN apt-get update --fix-missing && \
    (apt-get install -y --no-install-recommends asterisk python3 python3-pip ffmpeg curl || \
     (sleep 5 && apt-get update && apt-get install -y --no-install-recommends asterisk python3 python3-pip ffmpeg curl)) && \
    pip3 install --break-system-packages --no-cache-dir gTTS && \
    rm -rf /var/lib/apt/lists/*

COPY src ./src

# Cache dependencies and build the WAR file
RUN mvn clean package -DskipTests

# Expose Tomcat port (Cargo typically runs on 8081 in this project)
EXPOSE 8081

# Run the Tomcat server using the Cargo plugin (matches how it's run locally)
CMD ["mvn", "cargo:run"]
