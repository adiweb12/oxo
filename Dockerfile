FROM mingchen/android-build-box:latest

WORKDIR /app

# Set memory limits for Gradle so it doesn't crash the server
ENV GRADLE_OPTS="-Dorg.gradle.jvmargs=-Xmx512m -Dorg.gradle.daemon=false"

COPY requirements.txt .
RUN apt-get update && apt-get install -y python3-pip
RUN pip3 install -r requirements.txt

COPY . .

EXPOSE 10000
CMD ["python3", "main.py"]
