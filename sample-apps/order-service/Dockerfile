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

# OTel Java Agent 다운로드 (의존성 캐싱 이후, 소스 빌드 이전에 수행)
# dependency:copy: Maven 로컬 레포가 아닌 지정 경로에 직접 파일로 복사
RUN ./mvnw dependency:copy \
    -Dartifact=io.opentelemetry.javaagent:opentelemetry-javaagent:2.10.0:jar \
    -DoutputDirectory=/workspace \
    -Dmdep.stripVersion=true \
    -q

COPY src src
RUN ./mvnw package -DskipTests -q

# --- Runtime Stage ---
FROM eclipse-temurin:17-jre
WORKDIR /app

# 보안: non-root 유저 실행 (Debian 계열 문법)
RUN groupadd -r appgroup && useradd -r -g appgroup appuser
USER appuser

COPY --from=build /workspace/opentelemetry-javaagent.jar /app/otel-javaagent.jar
COPY --from=build /workspace/target/*.jar app.jar

EXPOSE 8080

# -javaagent로 OTel Agent 활성화, 나머지 설정은 K8s 환경변수로 주입
ENTRYPOINT ["java", \
  "-javaagent:/app/otel-javaagent.jar", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
