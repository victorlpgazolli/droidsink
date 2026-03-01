FROM gradle:8.5-jdk17

USER root

RUN apt-get update && apt-get install -y \
    build-essential \
    libusb-1.0-0-dev \
    libc6-dev \
    gcc-multilib

WORKDIR /app

COPY . .

RUN chmod +x gradlew

CMD ["bash"]