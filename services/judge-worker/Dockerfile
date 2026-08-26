# Multi-stage: build with the full JDK, run on the smaller JRE. Judge Worker
# is the first service in this repo that runs containerized itself (see the
# Phase 1 plan's decision to containerize it now, not defer to Phase 6) -
# every other service still runs as a plain local JVM process this phase.
FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /workspace
COPY gradlew gradlew.bat settings.gradle build.gradle gradle.properties ./
COPY gradle ./gradle
RUN ./gradlew --version
COPY src ./src
RUN ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=builder /workspace/build/libs/*.jar app.jar

# Runs as root, deliberately - not the non-root posture the sandbox images
# use. The mounted host /var/run/docker.sock is normally owned by root (or
# a docker group GID this image doesn't have), so a non-root user here
# would need matching GID plumbing for no real isolation benefit: this
# container already has root-equivalent control over the whole host Docker
# daemon through that socket regardless of which user PID 1 runs as. Same
# posture Jenkins/CI docker-in-docker builder images use. Named explicitly
# as a security trade-off in the Phase 1 plan, accepted for a dev-only
# deployment.
ENTRYPOINT ["java", "-jar", "app.jar"]
