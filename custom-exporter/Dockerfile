# --- Build Stage ---
# BUILDPLATFORM: 빌드를 실행하는 머신의 아키텍처 (M1 Mac = linux/arm64)
# Java 바이트코드는 플랫폼 독립적이므로 빌드는 네이티브로 실행해 속도를 유지한다.
FROM --platform=$BUILDPLATFORM eclipse-temurin:17-jdk AS build
WORKDIR /workspace

COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
# 의존성만 먼저 캐싱
RUN ./mvnw dependency:go-offline -q

COPY src src
RUN ./mvnw package -DskipTests -q

# --- Runtime Stage ---
FROM eclipse-temurin:17-jre
WORKDIR /app

# 보안: non-root 유저 실행 (Debian 계열 문법)
RUN groupadd -r appgroup && useradd -r -g appgroup appuser
USER appuser

COPY --from=build /workspace/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
