# syntax=docker/dockerfile:1.7
# Un solo Dockerfile per tutti i servizi: il modulo da costruire si passa con --build-arg MODULE=...

FROM maven:3.9.9-eclipse-temurin-21 AS build
ARG MODULE
WORKDIR /src
COPY . .
# cache di ~/.m2 tra una build e l'altra; -am costruisce anche le librerie da cui il modulo dipende
RUN --mount=type=cache,target=/root/.m2 \
    mvn -q -B -pl ${MODULE} -am package -DskipTests && \
    cp ${MODULE}/target/${MODULE}-*.jar /app.jar

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 1001 app
USER app
COPY --from=build /app.jar /app/app.jar
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
