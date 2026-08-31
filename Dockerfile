FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /app
COPY pom.xml .
<<<<<<< Updated upstream
COPY src ./src
RUN mvn -q package -DskipTests
=======
COPY src src
RUN apk add --no-cache maven && mvn -B clean package
>>>>>>> Stashed changes

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
<<<<<<< Updated upstream
COPY --from=build /app/target/le-market-james-0.0.1-SNAPSHOT.jar app.jar

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser
EXPOSE 8081
=======
COPY --from=build /app/target/le-market-james-1.0.0.jar app.jar

USER 10001
>>>>>>> Stashed changes
CMD ["java", "-jar", "app.jar"]