# syntax=docker/dockerfile:1

# Build and run the alumni Spring Boot service.
#
#   docker build -t sammalani/alumni-api ./server
#
# The runtime image carries a JRE, not a JDK, and no Gradle — nothing from the
# build stage survives except the extracted application.

# ---- build ------------------------------------------------------------------

# Gradle comes preinstalled rather than through ./gradlew: the wrapper would
# download the same 130 MB distribution on every cold build, and with
# `retries=0` in gradle-wrapper.properties one truncated read fails the build.
# The tag pins the same Gradle and the same JDK 25 the wrapper and the toolchain
# ask for — keep it in step with gradle/wrapper/gradle-wrapper.properties.
FROM gradle:9.6.1-jdk25-alpine AS build

ENV GRADLE_USER_HOME=/home/gradle/.gradle

WORKDIR /build

# Build scripts first. Resolving dependencies is the slow half of a cold build,
# and this way it only reruns when the build files change, not on every source
# edit.
COPY settings.gradle.kts build.gradle.kts ./
RUN --mount=type=cache,target=/home/gradle/.gradle,sharing=locked \
    gradle --no-daemon --console=plain dependencies --configuration runtimeClasspath

COPY src ./src
RUN --mount=type=cache,target=/home/gradle/.gradle,sharing=locked \
    gradle --no-daemon --console=plain bootJar

# The java plugin also emits <name>-plain.jar alongside the boot jar; only the
# boot jar is runnable, so pick it by exclusion rather than by version number.
RUN find build/libs -name '*.jar' ! -name '*-plain.jar' -exec cp {} application.jar \; \
 && java -Djarmode=tools -jar application.jar extract --layers --destination extracted

# ---- runtime ----------------------------------------------------------------

FROM eclipse-temurin:25-jre-alpine AS runtime

RUN addgroup -S alumni && adduser -S -G alumni alumni

WORKDIR /app

# Copied in order of how often they change: third-party libraries almost never,
# the application itself on every build. Docker then reuses the fat lower layers
# across deploys and ships only the last one.
COPY --from=build --chown=alumni:alumni /build/extracted/dependencies/ ./
COPY --from=build --chown=alumni:alumni /build/extracted/spring-boot-loader/ ./
COPY --from=build --chown=alumni:alumni /build/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=alumni:alumni /build/extracted/application/ ./

USER alumni

EXPOSE 8090

# The JVM sees the container limit, not the host's memory. Leave headroom for
# metaspace, thread stacks and the direct buffers Lettuce uses.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

# The readiness probe only reports UP once Flyway has migrated and the pool is
# open, so this says the service can serve traffic — not merely that a JVM is
# running. `depends_on: service_healthy` in compose is waiting on that.
HEALTHCHECK --interval=15s --timeout=3s --start-period=60s --retries=5 \
  CMD wget -qO- http://127.0.0.1:8090/actuator/health/readiness | grep -q '"status":"UP"'

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar application.jar"]
