FROM maven:3.9.11-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src src
RUN mvn -q -DskipTests package

FROM build AS healthcheck-build
COPY docker/Healthcheck.java docker/Healthcheck.java
RUN javac -d /workspace/healthcheck docker/Healthcheck.java

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN useradd --system --uid 10001 --no-create-home --shell /usr/sbin/nologin appuser
COPY --from=build /workspace/target/your-market-*.jar app.jar
COPY --from=healthcheck-build /workspace/healthcheck healthcheck
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
