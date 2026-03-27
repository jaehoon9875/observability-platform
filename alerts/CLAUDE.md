# alerts/ 디렉토리 가이드

## 개요

이 디렉토리는 Prometheus Alert Rule을 `PrometheusRule` K8s CRD 형식으로 관리한다.
kube-prometheus-stack의 Prometheus Operator가 이 리소스를 감지하여 자동으로 로드한다.

## 파일 구성

| 파일 | 설명 |
| --- | --- |
| `slo-alerts.yaml` | SLO 기반 알림 (에러율, 레이턴시) |
| `infra-alerts.yaml` | 인프라 상태 알림 (Pod 재시작, 리소스 사용량) |

## 클러스터 환경 정보

- **Prometheus Operator 라벨**: `release: my-kube-prometheus-stack`
  - PrometheusRule의 `metadata.labels`에 반드시 이 라벨이 있어야 Operator가 감지한다.
  - 라벨이 없으면 Rule이 Prometheus에 로드되지 않는다.
- **앱 네임스페이스**: `observability-platform`
- **모니터링 네임스페이스**: `monitoring`
- **PrometheusRule 배포 네임스페이스**: `monitoring`

## 적용 및 확인 방법

```bash
# Rule 적용
kubectl apply -f alerts/slo-alerts.yaml
kubectl apply -f alerts/infra-alerts.yaml

# 적용 확인
kubectl get prometheusrule -n monitoring

# Prometheus가 Rule을 로드했는지 확인
# Prometheus UI → Status → Rules 메뉴에서 확인
# 또는 Alerts 탭에서 정의된 Alert 목록 확인
```

## PrometheusRule 기본 구조

```yaml
apiVersion: monitoring.coreos.com/v1
kind: PrometheusRule
metadata:
  name: {rule-name}
  namespace: monitoring
  labels:
    release: my-kube-prometheus-stack  # 필수
spec:
  groups:
    - name: {group-name}
      rules:
        - alert: {AlertName}
          expr: {PromQL}   # 조건식
          for: 5m           # 조건 지속 시간
          labels:
            severity: critical | warning | info
          annotations:
            summary: "한 줄 요약"
            description: "상세 설명 ({{ $labels.xxx }}, {{ $value }} 변수 사용 가능)"
```

## severity 기준

| severity | 의미 |
| --- | --- |
| `critical` | 즉시 대응 필요 (SLO 위반, 서비스 다운) |
| `warning` | 주의 필요 (리소스 임계치 근접, 잠재적 장애 징후) |
| `info` | 참고 수준 정보 |

## 수집 중인 주요 메트릭 (PromQL 작성 시 참고)

### order-service / payment-service / notification-service
- `http_server_requests_seconds_count{job, uri, method, status}` — HTTP 요청 수
- `http_server_requests_seconds_bucket{job, uri}` — HTTP 응답시간 히스토그램
- `order_created_total` — 생성된 주문 수
- `order_failed_total` — 실패한 주문 수
- `order_processing_duration_seconds` — 주문 처리 소요 시간
- `payment_call_duration_seconds{status}` — payment-service 호출 시간

### Kubernetes (kube-state-metrics)
- `kube_pod_container_status_restarts_total{namespace, pod}` — Pod 재시작 횟수
- `kube_pod_status_ready{namespace, pod, condition}` — Pod Ready 상태

### cAdvisor (컨테이너 리소스)
- `container_memory_working_set_bytes{namespace, pod, container}` — 메모리 사용량
- `container_spec_memory_limit_bytes{namespace, container}` — 메모리 limit
- `container_cpu_usage_seconds_total{namespace, pod, container}` — CPU 사용량

## 주의사항

- `expr`의 메트릭명과 라벨은 실제 환경에서 `kubectl port-forward`로 Prometheus에 접속해 확인 후 사용한다.
- `job` 라벨 값은 ServiceMonitor의 `spec.jobLabel` 또는 서비스명을 따른다.
- `for` 값이 너무 짧으면 일시적 스파이크에도 알림이 발생하므로 최소 2~5분으로 설정한다.
