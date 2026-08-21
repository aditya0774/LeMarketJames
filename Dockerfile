FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /app
COPY Greeter.java .
RUN javac Greeter.java

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
COPY --from=build /app/Greeter.class .

USER 10001
CMD ["java", "Greeter"]