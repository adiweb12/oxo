# Use a working Android SDK + Gradle image
FROM circleci/android:api-30

# Set working directory
WORKDIR /app

# Gradle options to avoid memory issues
ENV GRADLE_OPTS="-Dorg.gradle.jvmargs=-Xmx512m -Dorg.gradle.daemon=false"

# Install Python and utilities
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

# Expose Flask port
EXPOSE 10000

# Run the Flask app
CMD ["python3", "main.py"]
