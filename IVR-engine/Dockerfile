# Use Maven and JDK 21 to build and run the application
FROM maven:3.9-eclipse-temurin-21

WORKDIR /app

# Copy the project files
COPY pom.xml .
COPY src ./src
COPY scenarios ./scenarios

# Install python3, ffmpeg, gTTS and SpeechRecognition so the engine's
# TTS synthesis (TtsEngine) and ASR (VxmlAgiHandler) subprocesses work in-container.
# Note: SpeechRecognition is pinned to 3.10.1 because newer versions
# (>=3.11) removed the recognize_google() backend used by VxmlAgiHandler.
# typing_extensions is required by the google recognizer but is not pulled in
# because SpeechRecognition is installed with --no-deps.
RUN apt-get update && apt-get install -y python3 python3-pip ffmpeg && \
    pip3 install --break-system-packages --no-cache-dir gTTS && \
    pip3 install --break-system-packages --no-cache-dir typing_extensions && \
    pip3 install --break-system-packages --no-cache-dir --no-deps "SpeechRecognition==3.10.1" requests && \
    rm -rf /var/lib/apt/lists/*

# Run Maven clean compile on image build to cache dependencies and compiled classes
RUN mvn clean compile

# Expose FastAGI port
EXPOSE 4573

# Run the FastAGI server using the exec plugin
CMD ["mvn", "exec:java", "-Dexec.mainClass=gov.iti.telecom.FastAgiServerMain"]
