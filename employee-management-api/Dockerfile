# syntax=docker/dockerfile:1

# ---- Build stage: compile and package with the full JDK ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace

# Dependencies are cached in their own layer: rebuilding after a source change
# does not re-download the world.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---- Runtime stage: JRE only, non-root user ----
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app
COPY --from=build /workspace/target/*.jar app.jar
RUN chown -R app:app /app
USER app

EXPOSE 8080

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
