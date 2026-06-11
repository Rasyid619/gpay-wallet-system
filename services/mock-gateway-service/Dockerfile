FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /workspace
COPY gradlew ./
COPY gradle ./gradle
COPY settings.gradle build.gradle ./
COPY services ./services
RUN chmod +x ./gradlew && ./gradlew :mock-gateway-service:bootJar -x test --no-daemon

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
COPY --from=build /workspace/services/mock-gateway-service/build/libs/*.jar app.jar
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
