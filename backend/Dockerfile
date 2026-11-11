# ---- build: compile and package the Spring Boot jar ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src src
# Tests need Docker (Testcontainers) and run in CI, not inside the image build.
RUN mvn -B -q -DskipTests package

# ---- run: JRE only, non-root user ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /workspace/target/webbank-backend-*.jar app.jar
USER app
# Free tiers give ~512 MB: keep the heap at 60% of it (metaspace and thread stacks need the rest),
# a single-threaded GC and C1-only JIT for a faster start and a smaller footprint.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k"
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
