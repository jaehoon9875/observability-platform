# incident-collector.sh 사용 가이드

`scripts/incident-collector.sh`는 장애 발생 시 Pod 진단에 필요한 정보를 한 번에 수집하는 셸 스크립트다.
`kubectl logs`, `describe`, `events`, `top` 을 Pod마다 반복 실행하는 수작업을 자동화한다.

---

## 동작 원리

```
./scripts/incident-collector.sh -n obs-apps
              │
              └── 네임스페이스 내 전체 Pod 대상
                       ↓
           1. Pod 목록 조회
                       ↓
           2. 네임스페이스 단위 수집
              - kubectl get pods -o wide
              - kubectl get events (시간순)
              - kubectl top pod
                       ↓
           3. Pod 단위 수집 (각 Pod 반복)
              - kubectl describe pod
              - kubectl logs (현재)
              - kubectl logs --previous (재시작 이력)
                       ↓
           4. 타임스탬프 디렉토리에 저장
              incident-reports/incident-20260330-1200/
```

---

## 옵션

| 옵션 | 설명 | 기본값 |
|---|---|---|
| `-n`, `--namespace <ns>` | 대상 네임스페이스 | `obs-apps` |
| `-a`, `--all-namespaces` | 전체 네임스페이스 대상 | - |
| `-o`, `--output <dir>` | 결과 저장 상위 디렉토리 | `./incident-reports` |
| `--lines <n>` | 수집할 로그 라인 수 | `200` |
| `-h`, `--help` | 도움말 출력 | - |

---

## 사용 예시

```bash
# obs-apps 네임스페이스 (기본값)
./scripts/incident-collector.sh

# 특정 네임스페이스
./scripts/incident-collector.sh -n monitoring

# 전체 네임스페이스
./scripts/incident-collector.sh --all-namespaces

# 로그 라인 수 조정 + 저장 위치 지정
./scripts/incident-collector.sh -n obs-apps --lines 500 -o /tmp/incidents
```

---

## 결과 디렉토리 구조

```
incident-reports/
└── incident-20260330-1200/
    ├── summary.txt                            ← 전체 Pod 상태 요약
    └── obs-apps/
        ├── pods-list.txt                      ← kubectl get pods -o wide
        ├── events.txt                         ← kubectl get events (시간순 정렬)
        ├── top-pods.txt                       ← kubectl top pod
        └── pods/
            ├── order-service-7d6f8b-xxx/
            │   ├── describe.txt               ← kubectl describe pod
            │   ├── logs-order-service.txt     ← 현재 컨테이너 로그
            │   └── logs-order-service-previous.txt  ← 재시작 직전 로그
            └── payment-service-5c9d7b-xxx/
                └── ...
```

### 각 파일 용도

| 파일 | 언제 주로 확인하나 |
|---|---|
| `summary.txt` | 첫 번째로 열어서 전체 Pod 상태 파악 |
| `events.txt` | OOMKilled, BackOff 등 클러스터 이벤트 확인 |
| `top-pods.txt` | CPU/메모리 이상 소비 Pod 식별 |
| `describe.txt` | Liveness/Readiness Probe 실패, 스케줄링 문제 확인 |
| `logs-*.txt` | 애플리케이션 에러 로그 확인 |
| `logs-*-previous.txt` | CrashLoopBackOff 등 재시작된 Pod의 직전 로그 확인 |

---

## 테스트 방법

### 정상 동작 확인

```bash
chmod +x ./scripts/incident-collector.sh
./scripts/incident-collector.sh -n obs-apps
```

### 장애 상황 시뮬레이션

의도적으로 OOM을 유발해 `logs-previous.txt`가 수집되는지 확인한다.

```bash
# 메모리 제한을 낮춰 OOM 유발
kubectl set resources deployment/order-service -n obs-apps \
  --limits=memory=10Mi

# CrashLoopBackOff 확인 후 스크립트 실행
kubectl get pods -n obs-apps
./scripts/incident-collector.sh -n obs-apps

# 결과에서 재시작 직전 로그 확인
cat incident-reports/incident-*/obs-apps/pods/order-service-*/logs-order-service-previous.txt

# 원복
kubectl set resources deployment/order-service -n obs-apps \
  --limits=memory=512Mi
```

---

## 참고

- `logs-*-previous.txt`가 비어있거나 "명령 실패"로 기록된 경우: 해당 Pod가 한 번도 재시작되지 않은 정상 상태임
- `top-pods.txt`가 비어있는 경우: 클러스터에 metrics-server가 설치되지 않은 상태임
- 스크립트는 명령 실패 시에도 중단하지 않고 다음 Pod 수집을 계속 진행함
