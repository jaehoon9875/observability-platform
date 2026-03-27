# tests/ 디렉토리 가이드

## 개요

이 디렉토리는 k6 부하 테스트 시나리오를 테스트 유형별로 관리한다.
테스트는 K8s Job으로 실행하며, 시나리오 스크립트는 ConfigMap으로 클러스터에 주입된다.

## 디렉토리 구성

| 경로 | 설명 |
| --- | --- |
| `load-test/order-flow.js` | 정상 트래픽 시나리오 — 점진적 VU 증가/유지/감소 |
| `spike-test/spike-test.js` | 급증 시나리오 — Alert Rule 트리거 검증용 |

K8s Job/ConfigMap 매니페스트는 `infra/k6/` 에서 관리한다.

## 클러스터 환경 정보

- **테스트 대상 네임스페이스**: `observability-platform`
- **order-service ClusterIP**: `kubectl get svc order-service -n observability-platform`
- **k6 이미지**: `grafana/k6:latest`
- **Job 실행 네임스페이스**: `observability-platform`

## 시나리오 설계 원칙

### load-test/order-flow.js (정상 트래픽)
- `POST /api/orders` 반복 호출
- VU 구성: 0 → 10 (ramp-up 1분) → 10 유지 (3분) → 0 (ramp-down 1분)
- thresholds:
  - `http_req_failed < 0.1%` (99.9% 가용성 SLO)
  - `http_req_duration{p(99)} < 500` (P99 500ms SLO)

### spike-test/spike-test.js (급증 시나리오)
- 갑작스러운 트래픽 급증으로 Alert Rule 트리거가 목표
- VU 구성: 5 (기준) → 20 (급증 30초) → 5 (회복)
- 확인 포인트: Prometheus Alert `HighErrorRate`, `HighP99Latency` 발생 여부

## 테스트 대상 API

```
POST /api/orders
Content-Type: application/json

{
  "productId": "1",
  "quantity": 1,
  "totalAmount": 10000
}
```

## 집중 관찰 메트릭 (PromQL 참고)

### SLO 관련
- `rate(http_server_requests_seconds_count{job="order-service", status=~"5.."}[1m])` — 에러율
- `histogram_quantile(0.99, rate(http_server_requests_seconds_bucket{job="order-service"}[5m]))` — P99 레이턴시

### 비즈니스 메트릭
- `rate(order_created_total[1m])` — 초당 주문 생성 수
- `rate(order_failed_total[1m])` — 초당 주문 실패 수

### JVM (부하 증가 시 관찰)
- `jvm_memory_used_bytes{area="heap"}` — Heap 사용량
- `jvm_gc_pause_seconds_sum` — GC 발생 빈도

## k6 메트릭 → Prometheus 연동 (선택)

k6 실행 시 `K6_PROMETHEUS_RW_SERVER_URL` 환경변수를 설정하면
`--out experimental-prometheus-rw` 옵션으로 k6 메트릭을 Prometheus로 전송할 수 있다.

- Remote Write URL: `http://prometheus-kube-prometheus-prometheus.monitoring.svc:9090/api/v1/write`
- 전송되는 주요 메트릭: `k6_http_req_duration`, `k6_http_req_failed`, `k6_vus`

## 주의사항

- k6 Job은 `restartPolicy: Never`로 설정한다. 실패 시 재시도하지 않도록.
- `order-service` 내부 ClusterIP를 사용하므로 Job과 같은 네임스페이스(`observability-platform`)에 배포한다.
- 시나리오 수정 후에는 ConfigMap을 먼저 업데이트(`kubectl apply`)한 뒤 Job을 재생성한다.
- 이전 Job이 남아있으면 같은 이름으로 생성되지 않으므로 `kubectl delete job` 후 재실행한다.
