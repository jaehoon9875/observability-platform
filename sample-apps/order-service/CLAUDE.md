# CLAUDE.md — order-service

## 이 서비스의 역할

주문 생성/조회 API를 제공하는 Spring Boot 애플리케이션.
payment-service를 호출하여 결제를 처리하고, notification-service에 알림을 요청한다.

## 의존 관계

```
order-service → payment-service (REST, 동기 호출)
order-service → notification-service (Kafka, 비동기)
order-service → MySQL (주문 데이터 저장)
order-service → Redis (주문 상태 캐싱)
```

## API 설계

- `POST /api/orders` — 주문 생성
- `GET /api/orders/{id}` — 주문 상세 조회
- `GET /api/orders` — 주문 목록 조회

## 커스텀 메트릭 (Micrometer)

이 서비스는 Prometheus가 수집할 비즈니스 메트릭을 `/actuator/prometheus`로 노출한다.

- `order_processing_duration_seconds` — 주문 처리 소요 시간 (histogram)
- `order_created_total` — 생성된 주문 수 (counter)
- `order_failed_total` — 실패한 주문 수 (counter)
- `payment_call_duration_seconds` — payment-service 호출 시간 (histogram)

## 분산 트레이싱

- OpenTelemetry Java Agent를 사용한다.
- trace-id는 HTTP 헤더로 전파하며, Tempo로 수집된다.
- Span 이름은 `{서비스명}.{메서드명}` 형식으로 한다.

## 주요 패키지 구조

```
src/main/java/com/example/order/
├── controller/     → REST API 엔드포인트
├── service/        → 비즈니스 로직
├── repository/     → JPA Repository
├── domain/         → Entity, VO
├── dto/            → Request/Response DTO
├── config/         → Spring 설정 (Kafka, Redis, Metrics 등)
├── client/         → 외부 서비스 호출 (payment-service)
└── exception/      → 커스텀 예외 및 핸들러
```

## 테스트

- 단위 테스트: service 레이어 중심, Mockito 사용
- 통합 테스트: `@SpringBootTest` + Testcontainers (MySQL)
- `./mvnw test` 로 실행
