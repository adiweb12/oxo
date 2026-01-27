FROM ghcr.io/android/gradle:7.6.2-jdk17

WORKDIR /app

ENV GRADLE_OPTS="-Dorg.gradle.jvmargs=-Xmx512m -Dorg.gradle.daemon=false"

# Install Python & unzip utilities
USER root
RUN apt-get update && apt-get install -y python3-pip zip unzip \
    && python3 -m pip install --upgrade pip

# Copy Python dependencies
COPY requirements.txt .
RUN python3 -m pip install -r requirements.txt

# Copy project files
COPY . .

# Make gradlew executable if it exists
RUN if [ -f ./gradlew ]; then chmod +x ./gradlew; fi

EXPOSE 10000

CMD ["python3", "main.py"]
