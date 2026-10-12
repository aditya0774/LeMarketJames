# Build context is the repo root (see docker-compose.yml) so Maven can see the parent pom, libs/common and libs/market-client.
FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /app
COPY pom.xml .
COPY libs libs
COPY services services
# Builds every module, not only this service: the line is the same in every service Dockerfile,
# so Docker runs it once and the other images take the layer from its cache. Keep it identical.
# The cache mount keeps Maven's downloads between builds, so a source change does not fetch every
# dependency again. It needs BuildKit, the default builder since Docker 23.
RUN --mount=type=cache,target=/root/.m2 mvn -q -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

WORKDIR /app
COPY --from=build /app/services/holdings-service/target/holdings-service-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8084
CMD ["java", "-jar", "app.jar"]
