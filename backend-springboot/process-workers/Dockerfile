# syntax=docker/dockerfile:1
# process-workers: Camunda 8 job workers (Spring Boot 4, no web server; polls Zeebe on the host).
# Build context is backend-springboot/ so the aggregator pom and sibling modules are visible:
#   docker build -f process-workers/Dockerfile -t process-workers .

# ---- build stage: JDK 25 (all modules set java.version 25; Boot 4 needs 17+) ----
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /src
COPY . .
# Builds only this module (-am adds nothing today: every module has its own Spring Boot parent).
RUN --mount=type=cache,target=/root/.m2 \
    mvn -q -B -pl process-workers -am package -DskipTests \
 && cp process-workers/target/process-workers-*.jar /tmp/app.jar

# ---- runtime stage: JRE 25 ----
FROM eclipse-temurin:25-jre
RUN groupadd --system app \
 && useradd --system --gid app --home-dir /app --no-create-home app \
 && mkdir -p /app && chown app:app /app
WORKDIR /app
COPY --from=build --chown=app:app /tmp/app.jar /app/app.jar
USER app
# no EXPOSE: web-application-type none, the app only makes outbound calls
ENTRYPOINT ["java","-jar","/app/app.jar"]
