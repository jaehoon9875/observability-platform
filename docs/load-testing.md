# k6 부하 테스트 가이드

이 문서는 k6를 K8s Job으로 실행하여 부하 테스트를 수행하는 방법을 설명한다.

---

## 구조

```
tests/
  load-test/
    order-flow.js   # 정상 트래픽 시나리오
  spike-test/
    spike-test.js   # 급증 시나리오

infra/k6/
  configmap.yaml    # 시나리오 스크립트를 담는 ConfigMap
  job.yaml          # k6 실행 Job
```

---

## 부하 설정값 (홈서버 기준으로 조정됨)

서버 사양에 따라 아래 값을 조정한다.
수정 위치: `tests/load-test/order-flow.js`, `tests/spike-test/spike-test.js`, `infra/k6/configmap.yaml` (세 파일 동일하게 유지)

### order-flow.js (정상 트래픽)

| 항목 | 현재값 | 의미 | 조정 기준 |
| --- | --- | --- | --- |
| 최대 VU | `10` | 동시 가상 유저 수 | 서버가 버거우면 5로 줄임 |
| ramp-up | `1m` | VU를 최대까지 올리는 시간 | 짧을수록 급격한 부하 |
| 유지 시간 | `3m` | 최대 VU를 유지하는 시간 | |
| sleep | `1s` | 요청 간 대기 시간 | 줄이면 req/s 증가 |
| **실효 req/s** | **~10** | VU ÷ sleep | |

### spike-test.js (급증 시나리오)

| 항목 | 현재값 | 의미 | 조정 기준 |
| --- | --- | --- | --- |
| 기준 VU | `5` | 평상시 트래픽 | |
| 급증 VU | `20` | 스파이크 트래픽 | 서버가 버거우면 10으로 줄임 |
| 급증 유지 | `2m` | Alert `for: 2m` 조건을 만족하기 위해 필요 | 줄이면 Alert 미트리거 |
| sleep | `0.3s` | 요청 간 대기 시간 | |
| **실효 req/s** | **~67** | VU ÷ sleep | |

---

## 사전 준비

테스트 대상 서비스가 정상 동작하는지 확인한다.

```bash
# order-service Pod 상태 확인
kubectl get pods -n observability-platform

# API 동작 확인 (포트포워딩 후)
kubectl port-forward svc/order-service 8080:8080 -n observability-platform
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{"productId": "1", "quantity": 1, "totalAmount": 10000}'
```

---

## 실행 방법

### 1. ConfigMap 적용 (시나리오 스크립트 클러스터에 주입)

```bash
kubectl apply -f infra/k6/configmap.yaml
```

### 2. Job 실행

```bash
# 정상 트래픽 시나리오 (job.yaml 기본값: order-flow.js)
kubectl apply -f infra/k6/job.yaml

# Job 로그 실시간 확인
kubectl logs -f job/k6-order-flow -n observability-platform
```

### 3. 급증 시나리오로 전환

`infra/k6/job.yaml`에서 두 곳을 수정한다:
- `metadata.name`: `k6-spike-test`
- `args`: `/scripts/spike-test.js`

```bash
kubectl delete job k6-order-flow -n observability-platform
kubectl apply -f infra/k6/job.yaml

kubectl logs -f job/k6-spike-test -n observability-platform
```

### 4. 완료 후 정리

`ttlSecondsAfterFinished: 300` 설정으로 완료 5분 후 자동 삭제된다.
수동으로 즉시 삭제하려면:

```bash
kubectl delete job k6-order-flow -n observability-platform
# 또는
kubectl delete job k6-spike-test -n observability-platform
```

---

## 시나리오 수정 후 재실행

스크립트를 수정한 경우 ConfigMap을 먼저 업데이트해야 한다.

```bash
kubectl apply -f infra/k6/configmap.yaml
kubectl delete job k6-order-flow -n observability-platform  # 또는 k6-spike-test
kubectl apply -f infra/k6/job.yaml
```

---

## 테스트 중 확인 포인트

### Grafana 대시보드

포트포워딩 후 접속:
```bash
kubectl port-forward svc/kube-prometheus-stack-grafana 3000:80 -n monitoring
# http://localhost:3000
```

| 대시보드 | 확인 내용 |
| --- | --- |
| **SLO Overview** | 에러율, P99 레이턴시 실시간 변화 |
| **JVM Analysis** | Heap 사용량, GC 빈도 증가 여부 |

### Prometheus Alerts

```bash
kubectl port-forward svc/kube-prometheus-stack-prometheus 9090:9090 -n monitoring
# http://localhost:9090/alerts
```

spike-test 실행 시 다음 Alert가 트리거되어야 한다:
- `HighErrorRate` — 에러율 > 1% for 2m
- `HighP99Latency` — P99 > 500ms for 2m

### Job 로그에서 k6 요약 결과 확인

Job 완료 후 로그에서 다음 항목을 확인한다:

```
http_req_duration............: avg=XXms  p(99)=XXXms
http_req_failed..............: X.XX%
http_reqs....................: XXXX XX/s
```

---

## k6 메트릭 → Prometheus 연동 (선택)

k6가 수집한 메트릭을 Prometheus로 전송하면 Grafana에서 시각화할 수 있다.

`infra/k6/job.yaml`에서 주석 처리된 두 환경변수를 해제하고,
`args`에 `"--out"`, `"experimental-prometheus-rw"`를 추가한다.

전송되는 주요 메트릭:
- `k6_http_req_duration` — 요청별 응답시간
- `k6_http_req_failed` — 실패율
- `k6_vus` — 현재 활성 VU 수
