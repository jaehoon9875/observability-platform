# --- Build Stage ---
FROM eclipse-temurin:17-jdk AS build
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

# OTel Java Agent (2단계에서 분산 트레이싱 활성화)
# COPY --from=build /workspace/otel-javaagent.jar /app/otel-javaagent.jar

COPY --from=build /workspace/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
