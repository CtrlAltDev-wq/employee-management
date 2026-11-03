# syntax=docker/dockerfile:1

# Build context is the repository root (see docker-compose.yml): the service
# depends on the shared contracts module, which lives in a sibling directory.

# ---- Build stage: compile and package with the full JDK ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace

# Every module POM is required because the root reactor lists all of them.
COPY pom.xml .
COPY contracts/pom.xml contracts/pom.xml
COPY employee-management-api/pom.xml employee-management-api/pom.xml
COPY onboarding-service/pom.xml onboarding-service/pom.xml
COPY api-gateway/pom.xml api-gateway/pom.xml
RUN mvn -B -q -pl onboarding-service -am dependency:go-offline

COPY contracts/src contracts/src
COPY onboarding-service/src onboarding-service/src
RUN mvn -B -q -pl onboarding-service -am clean package -DskipTests

# ---- Runtime stage: JRE only, non-root user ----
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app
COPY --from=build /workspace/onboarding-service/target/*.jar app.jar
RUN chown -R app:app /app
USER app

EXPOSE 8081

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD wget -qO- http://localhost:8081/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
