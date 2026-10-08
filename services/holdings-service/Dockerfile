# Build context is the repo root (see docker-compose.yml) so Maven can see the parent pom, libs/common and libs/market-client.
FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /app
COPY pom.xml .
COPY libs libs
COPY services services
RUN mvn -q -B -pl services/holdings-service -am package -DskipTests

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

WORKDIR /app
COPY --from=build /app/services/holdings-service/target/holdings-service-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8084
CMD ["java", "-jar", "app.jar"]
