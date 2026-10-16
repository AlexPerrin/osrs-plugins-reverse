# syntax=docker/dockerfile:1
FROM gradle:8.14.3-jdk21 AS build
WORKDIR /src
COPY rsprot ./rsprot
COPY settings.gradle.kts build.gradle.kts ./
COPY src ./src
RUN --mount=type=cache,target=/home/gradle/.gradle gradle --no-daemon installDist

FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl unzip && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /src/build/install/rsps-test-server /app
COPY protocol /app/protocol
COPY docker-entrypoint.sh /app/
# The login RSA key is baked in: the /data bind mount is not reliable under Docker Desktop (after a
# restart it can come up empty), and a regenerated key breaks every patched client.
COPY data/rsa.properties /app/rsa.properties
ENV RSA_FILE=/app/rsa.properties
ENV DATA_DIR=/data
VOLUME /data
EXPOSE 43594 8080
ENTRYPOINT ["/app/docker-entrypoint.sh"]
