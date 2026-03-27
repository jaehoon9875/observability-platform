# Observability 테스트 가이드

이 문서는 sample-apps (order/payment/notification-service)의 메트릭, 트레이싱, 로그를
로컬에서 수동 테스트하는 방법을 정리한다.

---

## 1. 사전 준비 — 포트포워딩

서비스에 직접 요청하려면 포트포워딩이 필요하다.
터미널을 분리하여 각각 실행한다.

```bash
# order-service (주요 진입점)
kubectl port-forward svc/order-service 8080:8080 -n obs-apps

# payment-service (직접 테스트 시)
kubectl port-forward svc/payment-service 8081:8080 -n obs-apps

# notification-service (직접 테스트 시)
kubectl port-forward svc/notification-service 8082:8080 -n obs-apps

# Grafana (로컬에서 접근 시)
kubectl port-forward svc/grafana 3000:80 -n monitoring
```

---

## 2. 트레이스 발생 — curl 요청

### 주문 생성 (전체 흐름 트리거)

order-service에 요청하면 아래 흐름이 자동으로 실행된다.
```
order-service → payment-service (REST 동기 호출)
order-service → Kafka 이벤트 발행 → notification-service (비동기 소비)
```

```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{"productId": "PRODUCT-001", "quantity": 2, "totalAmount": 19900}'
```

**성공 응답 예시:**
```json
{
  "status": "SUCCESS",
  "data": {
    "orderId": 1,
    "productId": "PRODUCT-001",
    "status": "COMPLETED"
  }
}
```

### 여러 건 연속 요청 (부하 발생용)

```bash
for i in {1..5}; do
  curl -s -X POST http://localhost:8080/api/orders \
    -H "Content-Type: application/json" \
    -d '{"productId": "PRODUCT-00'"$i"'", "quantity": 1, "totalAmount": 9900}'
  echo ""
done
```

### 주문 조회

```bash
# 전체 목록
curl http://localhost:8080/api/orders

# 단건 조회
curl http://localhost:8080/api/orders/1
```

---

## 3. Prometheus 메트릭 확인

### Actuator 엔드포인트 직접 확인

```bash
# order-service 메트릭 원문 확인
curl http://localhost:8080/actuator/prometheus | grep -E "payment_call|order_"

# 헬스 체크
curl http://localhost:8080/actuator/health
```

### Grafana — Prometheus 쿼리 (Explore → Prometheus)

| 목적 | PromQL |
|---|---|
| payment-service 호출 횟수 | `payment_call_duration_seconds_count` |
| payment-service 평균 응답시간 | `rate(payment_call_duration_seconds_sum[1m]) / rate(payment_call_duration_seconds_count[1m])` |
| 주문 생성 카운터 | `order_created_total` |
| 주문 실패 카운터 | `order_failed_total` |
| JVM Heap 사용량 | `jvm_memory_used_bytes{area="heap"}` |
| HTTP 요청 처리량 | `rate(http_server_requests_seconds_count[1m])` |
| HTTP 요청 에러율 | `rate(http_server_requests_seconds_count{status=~"5.."}[1m])` |

---

## 4. 분산 트레이스 확인

### Grafana — Tempo (Explore → Tempo 데이터소스 선택)

**Search 탭:**

| 필드 | 값 |
|---|---|
| Service Name | `order-service` / `payment-service` / `notification-service` |
| Tags → http.route | `/api/orders` |
| Tags → http.request.method | `POST` |
| Tags → http.response.status_code | `200` |

**TraceQL 탭:**

```
# order-service 전체 트레이스
{ resource.service.name = "order-service" }

# POST /api/orders 요청만
{ resource.service.name = "order-service" && span.http.route = "/api/orders" }

# 느린 요청 (100ms 이상)
{ resource.service.name = "order-service" } | duration > 100ms

# 에러 트레이스
{ resource.service.name = "order-service" && status = error }

# payment-service 호출 span
{ resource.service.name = "payment-service" && span.http.route = "/api/payments" }
```

### 정상 트레이스 구조

```
[order-service] POST /api/orders
  ├─ OrderRepository.save
  │    └─ INSERT orderdb.orders
  ├─ POST (payment-service 호출)
  │    └─ [payment-service] POST /api/payments
  │         ├─ PaymentRepository.save
  │         │    └─ INSERT paymentdb.payments
  │         └─ Transaction.commit
  ├─ order-completed (Kafka 이벤트 발행)
  │    └─ [notification-service] order-completed process
  │         ├─ NotificationRepository.save
  │         │    └─ INSERT notificationdb.notifications
  │         └─ Transaction.commit
  └─ Transaction.commit
       └─ UPDATE orderdb.orders
```

### OTel Agent 정상 동작 확인

```bash
# Agent 기동 메시지 확인
kubectl logs deployment/order-service -n obs-apps | grep -i "otel\|javaagent"

# 트레이스 전송 오류 없는지 확인
kubectl logs deployment/order-service -n obs-apps | grep -i "error" | grep -i "otel"
```

---

## 5. Kafka 비동기 통신 확인

### notification-service 로그 확인

```bash
# 실시간 로그 스트림
kubectl logs -f deployment/notification-service -n obs-apps

# 알림 처리 완료 메시지만 필터
kubectl logs deployment/notification-service -n obs-apps | grep "알림 처리"
```

**정상 로그 예시:**
```
INFO ... 알림 수신: orderId=1, productId=PRODUCT-001, amount=19900
INFO ... 알림 처리 완료: orderId=1
```

### Kafka 토픽 직접 확인

```bash
# Kafka 파드 접속
kubectl exec -it <kafka-pod> -n obs-apps -- bash

# 토픽 메시지 확인
kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic order-completed \
  --from-beginning
```

---

## 6. K8s 운영 명령어

### 파드 상태 확인

```bash
# 전체 파드 상태
kubectl get pods -n obs-apps

# 파드 상세 (이벤트 포함)
kubectl describe pod <pod-name> -n obs-apps
```

### 이미지 업데이트 후 재배포

```bash
# yaml 변경이 있을 때
kubectl apply -f infra/sample-apps/order-service/deployment.yaml

# yaml 변경 없이 이미지만 새로 당길 때
kubectl rollout restart deployment/order-service -n obs-apps

# 롤아웃 진행 상태 확인
kubectl rollout status deployment/order-service -n obs-apps
```

### 환경변수 주입 확인

```bash
# OTel 환경변수 확인
kubectl exec -it <pod-name> -n obs-apps -- env | grep OTEL
```

---

## 7. 주요 설정 참고

| 항목 | 값 |
|---|---|
| OTel Agent 버전 | 2.10.0 |
| Tempo 트레이스 수신 포트 | 4318 (http/protobuf) |
| Tempo 쿼리 포트 | 3200 |
| Grafana Tempo 데이터소스 URL | `http://tempo:3200` |
| OTel 프로토콜 | http/protobuf (기본값, Agent 1.27+) |

> **참고:** OTel Java Agent 1.27부터 기본 프로토콜이 `grpc(4317)` → `http/protobuf(4318)`로 변경됨.
> 포트를 4317로 설정하면 `Connection reset` 오류 발생.
