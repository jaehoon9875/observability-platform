# run-k6.sh 사용 가이드

`scripts/run-k6.sh`는 k6 부하 테스트의 실행 사이클을 자동화한 셸 스크립트다.
파일을 직접 수정하지 않고 **인자만으로 부하 설정을 제어**할 수 있다.

---

## 동작 원리

```
./scripts/run-k6.sh spike-test --vus 40 --sleep 0.1
           │               │         │
           │               │         └── 환경변수 SLEEP=0.1 로 Job에 주입
           │               └──────────── 환경변수 SPIKE_VUS=40 으로 Job에 주입
           └──────────────────────────── 실행할 시나리오 선택
```

스크립트 내부에서 순서대로 다음을 수행한다:

```
1. 이전 Job 자동 삭제  (같은 이름의 Job이 남아있으면)
       ↓
2. ConfigMap 적용       (kubectl apply -f infra/k6/configmap.yaml)
       ↓
3. Job 동적 생성        (인자로 받은 값을 env로 주입한 YAML을 즉석 생성)
                        backoffLimit: 0 으로 실패 시 재시작 없음
       ↓
4. Pod 준비 대기
       ↓
5. 로그 실시간 출력     (Ctrl+C로 중단해도 Job은 클러스터에서 계속 실행됨)
```

k6 스크립트는 실행 시 `__ENV.SPIKE_VUS` 같은 환경변수를 읽어 부하 설정을 결정한다.
파일 수정 없이 인자로 전달한 값이 그대로 반영된다.

---

## 기본 사용법

```bash
# 서버에서 실행 (프로젝트 루트에서)
./scripts/run-k6.sh <시나리오> [옵션]
```

### order-flow (정상 트래픽)

```bash
# 기본값으로 실행 (10 VU, 5분)
./scripts/run-k6.sh order-flow

# VU 수 변경
./scripts/run-k6.sh order-flow --vus 20

# VU + 요청 간격 변경
./scripts/run-k6.sh order-flow --vus 20 --sleep 0.5

# 전체 옵션
./scripts/run-k6.sh order-flow --vus 20 --sleep 0.5 --rampup 2m --sustain 5m --rampdown 1m
```

### spike-test (급증 시나리오)

```bash
# 기본값으로 실행 (기준 5 VU → 급증 20 VU)
./scripts/run-k6.sh spike-test

# 급증 VU만 높이기
./scripts/run-k6.sh spike-test --vus 40

# 더 빠른 요청 속도로
./scripts/run-k6.sh spike-test --vus 40 --sleep 0.1

# 기준 트래픽도 조정
./scripts/run-k6.sh spike-test --base-vus 10 --vus 50
```

---

## 옵션 레퍼런스

### 공통

| 옵션 | 설명 | 기본값 |
| --- | --- | --- |
| `--sleep <초>` | 요청 간 대기 시간. 줄이면 req/s 증가 | order-flow: `1.0` / spike-test: `0.3` |

### order-flow 전용

| 옵션 | 설명 | 기본값 |
| --- | --- | --- |
| `--vus <n>` | 최대 동시 가상 유저 수 | `10` |
| `--rampup <시간>` | 0 → 최대 VU까지 올리는 시간 | `1m` |
| `--sustain <시간>` | 최대 VU를 유지하는 시간 | `3m` |
| `--rampdown <시간>` | 최대 VU → 0까지 내리는 시간 | `1m` |

### spike-test 전용

| 옵션 | 설명 | 기본값 |
| --- | --- | --- |
| `--vus <n>` | 급증 구간의 최대 VU 수 | `20` |
| `--base-vus <n>` | 평상시(기준) VU 수 | `5` |

---

## req/s 계산

```
req/s ≈ VU 수 ÷ sleep 시간
```

| VU | sleep | req/s |
| --- | --- | --- |
| 10 | 1.0s | ~10 |
| 20 | 0.5s | ~40 |
| 40 | 0.3s | ~133 |
| 40 | 0.1s | ~400 |

홈서버 기준으로 50~100 req/s 정도가 Alert 트리거에 적당했다.

---

## Alert 트리거 확인

spike-test 실행 후 Prometheus에서 Alert 상태를 확인한다.

```bash
kubectl port-forward svc/kube-prometheus-stack-prometheus 9090:9090 -n monitoring
# http://localhost:9090/alerts
```

트리거 목표:
- `HighErrorRate` — 에러율 > 1% for 2m
- `HighP99Latency` — P99 > 500ms for 2m

급증 유지 구간이 2분(`{ duration: '2m', target: SPIKE_VUS }`)으로 고정되어 있어,
그 안에 Alert 조건(`for: 2m`)을 만족하면 발화한다.

---

## 테스트 중단

```bash
# 전체 k6 Job 중단
./scripts/run-k6.sh stop

# 특정 시나리오만 중단
./scripts/run-k6.sh stop order-flow
./scripts/run-k6.sh stop spike-test
```

Job을 삭제하면 실행 중인 Pod도 함께 종료된다.

---

## 실행 중 Job 확인

Ctrl+C로 로그 스트리밍을 중단해도 Job은 클러스터에서 계속 실행된다.

```bash
# Job 상태 확인
kubectl get job -n obs-apps

# 로그 다시 붙기
kubectl logs -f job/k6-order-flow -n obs-apps
kubectl logs -f job/k6-spike-test -n obs-apps
```

완료된 Job은 `ttlSecondsAfterFinished: 300` 설정으로 5분 후 자동 삭제된다.
`backoffLimit: 0`이 설정되어 있어 k6가 실패해도 Pod를 재생성하지 않는다.
