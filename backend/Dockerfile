# syntax=docker/dockerfile:1.7
# ── Build ────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
# Resolve dependencies first so code changes don't re-download them
COPY gradlew build.gradle.kts settings.gradle.kts ./
COPY gradle ./gradle
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true
COPY src ./src
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon bootJar -x test \
 && cp build/libs/*.jar app.jar \
 && java -Djarmode=tools -jar app.jar extract --layers --destination extracted

# ── Run ──────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S -G app app
WORKDIR /app
# Layered copy: dependencies change rarely, application code often → small pushes
COPY --from=build --chown=app:app /src/extracted/dependencies/ ./
COPY --from=build --chown=app:app /src/extracted/spring-boot-loader/ ./
COPY --from=build --chown=app:app /src/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=app:app /src/extracted/application/ ./
USER app
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseContainerSupport -Djava.security.egd=file:/dev/./urandom" \
    PORT=8080
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://127.0.0.1:${PORT}/actuator/health | grep -q '"UP"' || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
