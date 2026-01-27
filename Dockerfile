FROM bitriseio/android-sdk:33

WORKDIR /app

ENV GRADLE_OPTS="-Dorg.gradle.jvmargs=-Xmx1024m -Dorg.gradle.daemon=false"

USER root

RUN apt-get update && apt-get install -y \
    python3 \
    python3-pip \
    zip \
    unzip \
    && python3 -m pip install --upgrade pip

COPY requirements.txt .
RUN python3 -m pip install -r requirements.txt

COPY . .

RUN if [ -f ./gradlew ]; then chmod +x ./gradlew; fi

EXPOSE 10000

CMD ["python3", "app.py"]
