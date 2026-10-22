FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn --batch-mode --no-transfer-progress -DskipTests package

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system app \
    && useradd --system --gid app --home-dir /app app
COPY --from=build --chown=app:app /build/target/projekt-0.0.1-SNAPSHOT.jar ./app.jar
USER app
ENV SPRING_PROFILES_ACTIVE=docker
EXPOSE 8081
HEALTHCHECK --interval=10s --timeout=5s --start-period=60s --retries=6 \
    CMD curl --fail --silent http://127.0.0.1:8081/api/books > /dev/null || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
