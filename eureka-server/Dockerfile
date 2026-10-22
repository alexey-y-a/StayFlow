FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY lombok.config .
COPY common/pom.xml common/
COPY eureka-server/pom.xml eureka-server/
COPY api-gateway/pom.xml api-gateway/
COPY hotel-service/pom.xml hotel-service/
COPY booking-service/pom.xml booking-service/
COPY eureka-server/src eureka-server/src
RUN mvn -q -pl eureka-server -am -Dmaven.test.skip=true package

FROM eclipse-temurin:21-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd -r -u 1001 appuser
WORKDIR /app
COPY --from=build --chown=appuser:appuser /workspace/eureka-server/target/eureka-server-0.1.0-SNAPSHOT.jar app.jar
USER appuser
EXPOSE 8761
HEALTHCHECK --interval=10s --timeout=3s --retries=12 --start-period=30s \
    CMD curl -fsS http://localhost:8761/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
