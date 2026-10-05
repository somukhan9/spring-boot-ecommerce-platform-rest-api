# Build any module:  docker build --build-arg SERVICE=auth-service .
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY . .
ARG SERVICE
RUN mvn -q -B -pl ${SERVICE} -am -DskipTests package

FROM eclipse-temurin:17-jre
ARG SERVICE
WORKDIR /app
RUN useradd --system --no-create-home appuser
COPY --from=build /app/${SERVICE}/target/${SERVICE}-1.0.0.jar app.jar
USER appuser
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
