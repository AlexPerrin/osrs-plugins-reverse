# syntax=docker/dockerfile:1
# OpenRune-Server runtime image. The build itself runs beforehand, also in Docker, through
# scripts/openrune.sh build; this image only packages its outputs (installDist, the packed caches,
# gamevals and the login RSA key) from src/. See README.md.
FROM eclipse-temurin:21-jre
# The embedded PostgreSQL refuses to run as root; uid 1000 is the image's "ubuntu" user.
WORKDIR /app
COPY --chown=1000:1000 src/server/app/build/install/app /app/server
COPY --chown=1000:1000 src/.data /app/.data
COPY --chown=1000:1000 src/game.yml /app/game.yml
COPY --chown=1000:1000 docker/entrypoint.sh /app/entrypoint.sh
# The login RSA key is baked in (as for our own server): every patched client carries its modulus.
# Saves live in PostgreSQL, whose data directory is a named volume (see docker-compose.yml).
RUN mkdir -p /app/.data/postgres /app/plugins /app/logs && chown -R 1000:1000 /app/.data/postgres /app/plugins /app/logs
USER 1000:1000
ENV HOME=/app
EXPOSE 43594
VOLUME /app/.data/postgres
ENTRYPOINT ["/app/entrypoint.sh"]
