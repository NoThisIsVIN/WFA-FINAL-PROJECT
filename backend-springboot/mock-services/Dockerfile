# syntax=docker/dockerfile:1
# mock-services: mock registries, weather, grid and e-sign (posts the signed-contract webhook to the
# connector runtime on the host).
# Build context is backend-springboot/ so the aggregator pom and sibling modules are visible:
#   docker build -f mock-services/Dockerfile -t mock-services .

# ---- build stage: JDK 25 (all modules set java.version 25) ----
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /src
COPY . .
# Builds only this module (-am adds nothing today: every module has its own Spring Boot parent).
RUN --mount=type=cache,target=/root/.m2 \
    mvn -q -B -pl mock-services -am package -DskipTests \
 && cp mock-services/target/mock-services-*.jar /tmp/app.jar

# ---- runtime stage: JRE 25 ----
FROM eclipse-temurin:25-jre
# curl is used by the compose healthcheck
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/*
RUN groupadd --system app \
 && useradd --system --gid app --home-dir /app --no-create-home app \
 && mkdir -p /app && chown app:app /app
WORKDIR /app
COPY --from=build --chown=app:app /tmp/app.jar /app/app.jar
USER app
EXPOSE 8091
ENTRYPOINT ["java","-jar","/app/app.jar"]
