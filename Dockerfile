# syntax=docker/dockerfile:1.6

ARG MAVEN_IMAGE=maven:3.9-eclipse-temurin-21
ARG JVM_IMAGE=eclipse-temurin:21-jre

FROM ${MAVEN_IMAGE} AS builder
WORKDIR /workspace

# First, copy the local Dapr SNAPSHOT artifacts
COPY .m2/repository/io/dapr /root/.m2/repository/io/dapr

COPY pom.xml .

# Download dependencies with cache mount (Dapr artifacts are already in place)
RUN --mount=type=cache,target=/tmp/.m2,sharing=locked \
    mvn dependency:go-offline -B || true

COPY src ./src

# Build with writable m2 repository
RUN mvn -B -DskipTests package


FROM ${JVM_IMAGE} AS runtime
WORKDIR /app

RUN apt-get update && apt-get install -y iproute2

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

COPY --from=builder /workspace/target/*.jar /app/app.jar

EXPOSE 8080 3000

ENTRYPOINT ["java","-jar","/app/app.jar"]



