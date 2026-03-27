# CLAUDE.md — sample-apps

## 코딩 컨벤션

- Java 코드는 Google Java Style Guide를 따른다.
- 클래스명은 PascalCase, 메서드/변수명은 camelCase.
- REST API 응답은 `ApiResponse<T>` 공통 포맷으로 감싼다.
- 예외 처리는 `@RestControllerAdvice`로 중앙 집중 처리한다.
- 모든 서비스 간 통신은 RestTemplate 또는 OpenFeign을 사용한다.

## 서비스 구성

Observability 플랫폼의 관측 대상이 되는 MSA 샘플 애플리케이션 3종.
각 서비스는 독립적으로 빌드/배포 가능하다.

| 서비스 | 역할 | 이미지 |
|---|---|---|
| order-service | 주문 생성/조회 API, payment-service 호출, Kafka 이벤트 발행 | `jaehoon9875/order-service:latest` |
| payment-service | 결제 처리 API (order-service에서 REST 호출) | `jaehoon9875/payment-service:latest` |
| notification-service | Kafka `order-completed` 토픽 구독 후 알림 처리 | `jaehoon9875/notification-service:latest` |

## 서비스 간 통신

```
order-service → payment-service   (REST, 동기)
order-service → notification-service   (Kafka, 비동기)
order-service → MySQL, Redis
payment-service → MySQL
notification-service → Kafka Consumer
```

## 빌드 및 테스트

```bash
# 테스트 실행
./mvnw test

# 패키지 빌드 (테스트 스킵)
./mvnw clean package -DskipTests
```

## Docker 빌드 및 Push

개발 환경이 Mac M1(arm64)이고 배포 환경이 Linux(amd64)이므로 멀티 플랫폼 빌드를 사용한다.
`multiarch` buildx 빌더가 사전에 생성되어 있어야 한다.

```bash
# multiarch 빌더가 없을 경우 최초 1회 생성
docker buildx create --name multiarch --driver docker-container --use

# 멀티 플랫폼 빌드 + Docker Hub push (각 서비스 디렉토리에서 실행)
docker buildx build \
  --builder multiarch \
  --platform linux/amd64,linux/arm64 \
  --push \
  -t jaehoon9875/{service-name}:latest .
```

## Kubernetes 배포

네임스페이스: `observability-platform`

```bash
# 매니페스트 적용 (최초 배포 또는 K8s 설정 변경 시)
kubectl apply -f infra/sample-apps/{service-name}/

# 새 이미지 반영 (이미지만 변경됐을 때 — imagePullPolicy: Always)
kubectl rollout restart deployment/{service-name} -n observability-platform

# 롤아웃 완료 확인
kubectl rollout status deployment/{service-name} -n observability-platform
```

## 커스텀 메트릭 (order-service)

`/actuator/prometheus`로 노출되며 Prometheus가 수집한다.

- `order_processing_duration_seconds` — 주문 처리 소요 시간 (histogram)
- `order_created_total` — 생성된 주문 수 (counter)
- `order_failed_total` — 실패한 주문 수 (counter)
- `payment_call_duration_seconds` — payment-service 호출 시간 (histogram)

## 분산 트레이싱

모든 서비스에 OpenTelemetry Java Agent가 적용되어 있다.
trace-id는 HTTP 헤더로 전파되며, Tempo로 수집된다.

## 코드 작성 규칙

- 요청하지 않은 리팩토링 금지.
- 라이브러리 임의 추가 금지.
- 파일 구조 임의 변경 금지.
- 한번에 하나의 기능만 구현할 것.
- 클래스와 메서드 단위로 한글 주석을 작성할 것.
