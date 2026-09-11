# syntax=docker/dockerfile:1.7

FROM maven:3.9.11-eclipse-temurin-17-alpine AS build
WORKDIR /workspace

COPY pom.xml ./
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn --batch-mode -Dmaven.test.skip=true package

FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app

COPY --from=build /workspace/target/agent-collab-0.1.0-SNAPSHOT.jar app.jar

USER app:app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
