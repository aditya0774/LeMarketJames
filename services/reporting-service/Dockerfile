# Build context is the repo root (see docker-compose.yml) so Maven can see the parent pom and libs/common.
FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /app
COPY pom.xml .
COPY libs libs
COPY services services
# The cache mount keeps downloaded dependencies between builds (BuildKit).
RUN --mount=type=cache,target=/root/.m2 mvn -q -B -pl services/reporting-service -am package -DskipTests

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

WORKDIR /app
COPY --from=build /app/services/reporting-service/target/reporting-service-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8086
CMD ["java", "-jar", "app.jar"]
