FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /workspace/app

COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

RUN chmod +x ./gradlew

RUN ./gradlew dependencies --no-daemon

COPY src src

RUN ./gradlew bootJar -x test --no-daemon && \
    mkdir -p build/dependency && \
    (cd build/dependency; jar -xf ../libs/*.jar)

FROM eclipse-temurin:17-jre-alpine
VOLUME /tmp

RUN addgroup -g 1001 -S spring && \
    adduser -u 1001 -S spring -G spring

ARG DEPENDENCY=/workspace/app/build/dependency
COPY --from=build --chown=spring:spring ${DEPENDENCY}/BOOT-INF/lib /app/lib
COPY --from=build --chown=spring:spring ${DEPENDENCY}/META-INF /app/META-INF
COPY --from=build --chown=spring:spring ${DEPENDENCY}/BOOT-INF/classes /app

USER spring:spring

ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseSerialGC -XX:+UseContainerSupport -XX:MaxRAMPercentage=80.0"

HEALTHCHECK --interval=30s --timeout=10s --start-period=30s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -cp /app:/app/lib/* com.project.catxi.CatxiApplication"]
