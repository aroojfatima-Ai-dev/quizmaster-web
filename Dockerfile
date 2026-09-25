# ---------------------------------------------------------------------------
# QuizMaster Web - production image for Railway.
#
# Stage 1 builds the fat jar with Maven, stage 2 runs it on a JRE only, so the
# shipped image stays small. Railway injects PORT; Spring Boot reads it via
# server.port=${PORT:8080} in application.properties.
# ---------------------------------------------------------------------------

# ---------- build ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Dependencies first: this layer is cached until pom.xml changes.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline -DskipTests \
    || echo "Some plugins resolve during package; continuing."

COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---------- run ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

# curl is only used by the health probe below.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd -r quizmaster \
    && useradd -r -g quizmaster quizmaster

COPY --from=build /build/target/quizmaster-web.jar /app/quizmaster-web.jar

ENV PORT=8080 \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC" \
    QUIZMASTER_DATA_DIR=/app/data

RUN mkdir -p /app/data && chown -R quizmaster:quizmaster /app
USER quizmaster

EXPOSE 8080

# Railway probes /healthz itself (see railway.json); this keeps `docker run`
# honest on any other host as well.
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
  CMD curl -fsS "http://127.0.0.1:${PORT:-8080}/healthz" || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/quizmaster-web.jar"]
