FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

COPY pom.xml ./
COPY src ./src
# Tests run separately with their required environment configuration.
RUN mvn -B -ntp -DskipTests package

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

RUN groupadd --system app && useradd --system --gid app app \
    && mkdir -p /app/storage && chown app:app /app/storage
COPY --from=build --chown=app:app /build/target/backend-*.jar /app/app.jar

USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
