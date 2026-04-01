# 실행 계획

이 문서는 observability-platform 프로젝트의 단계별 실행 계획이다.
각 단계는 순서대로 진행하며, 이전 단계가 완료된 후 다음 단계를 시작한다.

---

## 1단계: order-service 개발 및 배포

### 목표

Observability 스택의 관측 대상이 되는 첫 번째 MSA 앱을 만든다.

### 할 일

1. ✅ Spring Boot 프로젝트 생성 (Maven, Java 17, Spring Boot 3.x)
  - 의존성: Spring Web, Spring Data JPA, MySQL Driver, Spring Boot Actuator, Micrometer Prometheus, SpringDoc OpenAPI (Swagger)
2. ✅ 기본 API 구현
  - `POST /api/orders` — 주문 생성
  - `GET /api/orders/{id}` — 주문 상세 조회
  - `GET /api/orders` — 주문 목록 조회
3. ✅ 커스텀 비즈니스 메트릭 추가 (Micrometer)
  - `order_processing_duration_seconds` — 주문 처리 소요 시간 (Timer)
  - `order_created_total` — 생성된 주문 수 (Counter)
  - `order_failed_total` — 실패한 주문 수 (Counter)
4. ✅ Dockerfile 작성 (멀티 스테이지 빌드, non-root 실행)
5. ✅ K8s 매니페스트 작성 (Deployment, Service, ConfigMap, ServiceMonitor)
6. ✅ 클러스터에 배포 후 Prometheus 스크랩 확인
7. ✅ Grafana에서 커스텀 메트릭 조회 확인

### 구현 메모

- `local` 프로파일: H2 인메모리 DB로 외부 의존성 없이 실행 가능 (`-Dspring.profiles.active=local`)
- `payment_call_duration_seconds` 메트릭도 함께 구현됨 (CLAUDE.md 설계 포함)
- OTel Java Agent는 Dockerfile에 주석 처리됨 → 2단계에서 활성화
- K8s 매니페스트: `infra/sample-apps/order-service/` (ConfigMap, Deployment, Service, ServiceMonitor)
- DB 인증 정보는 Secret으로 분리 — `kubectl apply` 전에 클러스터에 먼저 생성 필요
  ```bash
  kubectl create secret generic order-service-secret \
    --from-literal=DB_USERNAME=root \
    --from-literal=DB_PASSWORD=<password> \
    -n obs-apps
  ```

### 완료 기준

- API가 정상 동작한다.
- Grafana에서 order_created_total 등 커스텀 메트릭이 보인다.

---

## 2단계: payment-service, notification-service 개발

### 목표

서비스 간 통신이 있는 분산 환경을 구성한다.

### 할 일

1. ✅ payment-service 개발
  - `POST /api/payments` — 결제 처리
  - order-service에서 REST로 호출
2. ✅ notification-service 개발
  - Kafka Consumer로 주문 완료 이벤트를 수신하여 알림 처리
3. ✅ 서비스 간 통신 메트릭 추가
  - `payment_call_duration_seconds` — payment-service 호출 시간
4. ✅ OpenTelemetry Java Agent 연동 (분산 트레이싱)
5. ✅ 각 서비스 Dockerfile + K8s 매니페스트 작성 및 배포

### 완료 기준

- order → payment 호출 시 Tempo에서 분산 트레이스가 보인다.
- Kafka를 통한 비동기 통신이 동작한다.

---

## 3단계: 대시보드 및 Alert Rule 설계

### 목표

SLO 기반 모니터링 체계를 구축한다.

### 할 일

1. ✅ SLO 정의
  - order-service 가용성 99.9%, 응답시간 P99 < 500ms
2. ✅ Grafana 대시보드 설계 및 JSON 저장
  - SLO 현황 대시보드 (`dashboards/slo-overview.yaml`) — 가용성, 에러율, P99, 주문 처리 현황
  - JVM 분석 대시보드 (`dashboards/jvm-analysis.yaml`) — Heap/Non-Heap, GC, Thread
  - 대시보드는 ConfigMap(PrometheusRule 방식과 동일)으로 관리, `grafana_dashboard: "1"` 라벨로 자동 로드
3. ✅ Prometheus Alert Rule 작성
  - SLO 위반 시 알림 (`alerts/slo-alerts.yaml`) — 에러율, P99 레이턴시, payment 호출 실패율
  - Pod 리소스 이상 감지 알림 (`alerts/infra-alerts.yaml`) — CrashLoop, NotReady, 메모리/CPU
  - PrometheusRule CRD로 관리, `release: my-kube-prometheus-stack` 라벨로 자동 로드
4. ✅ dashboards/ 와 alerts/ 디렉토리에 파일 저장

### 완료 기준

- Grafana 대시보드에서 SLO 현황을 한눈에 볼 수 있다. ✅
- Alert Rule이 Prometheus에 로드되어 있다. ✅

---

## 4단계: k6 부하 테스트

### 목표

트래픽을 시뮬레이션하여 병목 지점을 찾고, 대시보드/알림이 제대로 동작하는지 검증한다.

### 할 일

1. ✅ k6 테스트 시나리오 작성
  - 정상 트래픽 시나리오 (order-flow.js)
  - 급증(spike) 시나리오 (spike-test.js)
2. ✅ 부하 테스트 실행 후 Grafana에서 메트릭 변화 확인
  - SLO Overview: 에러율 0%, req/s 변화 확인
  - JVM Analysis: GC 일시정지 시간 증가, 스레드 상태별 현황 변화 확인
3. 병목 지점 분석 및 문서화
4. (선택) k6 결과를 Prometheus로 내보내 Grafana에서 시각화

### 완료 기준

- 부하 테스트 중 대시보드에서 트래픽 변화가 실시간으로 보인다.
- Alert Rule이 트리거된다.

---

## 5단계: infra/ GitOps 전환 (ArgoCD)

### 목표

수동으로 설치한 Observability 스택과 sample-apps를 ArgoCD 기반 GitOps로 전환한다.

### 배경

ArgoCD는 Git 리포지토리를 바라보며 infra/ 디렉토리의 변경을 감지해 클러스터에 자동으로 동기화한다.
이미지 빌드는 CI(6단계)가 담당하고, ArgoCD는 선언적 상태 관리만 책임진다.

### 할 일

1. ✅ ArgoCD 설치 (클러스터에 배포)
2. ✅ 현재 Helm으로 설치된 Observability 스택의 values 추출 및 infra/ 정리
   - `infra/helm/kube-prometheus-stack/values.yaml`
   - `infra/helm/loki/values.yaml`
   - `infra/helm/tempo/values.yaml`
   - `infra/alloy/values.yaml` (필요 시)
3. ✅ ArgoCD Application 매니페스트 작성 (`infra/argocd/`)
   - Observability 스택 Application
   - sample-apps Application
4. ✅ Git push → ArgoCD 자동 동기화 확인
   - Synced: alloy, kafka, kafka-ui, loki, mysql, redis, sample-apps, strimzi-operator, tempo
   - OutOfSync (미해결): mysql-operator, prometheus-stack → 아래 TODO 참조
5. ✅ sample-apps의 기존 kubectl apply 방식을 ArgoCD로 완전 전환

### 완료 기준

- `infra/` 디렉토리의 values.yaml을 수정하고 push하면 클러스터에 자동 반영된다.
- ArgoCD UI에서 모든 Application이 Synced 상태로 표시된다.

### 미해결 이슈

미해결 이슈는 [docs/ISSUES.md](ISSUES.md)에서 관리한다.

- mysql-operator CRD OutOfSync
- prometheus-stack Grafana SSA 충돌

---

## 6단계: GitHub Actions CI 파이프라인

### 목표

sample-apps의 소스 코드 변경 시 이미지 빌드부터 배포까지 자동화한다.
실무 GitOps 흐름(코드 커밋 → CI 빌드 → ArgoCD 배포)을 완성한다.

### 전체 흐름

```
개발자 코드 커밋 (sample-apps/)
  ↓
GitHub Actions (GitHub 서버에서 실행)
  ├── 단위 테스트
  ├── Docker 이미지 빌드
  └── GHCR(GitHub Container Registry) push
       ↓
       infra/sample-apps/.../deployment.yaml 이미지 태그 업데이트 커밋
         ↓
         ArgoCD 감지 → 클러스터 자동 배포
```

### 할 일

1. ✅ GHCR 연동 설정 (GitHub Actions에서 GHCR 인증)
2. ✅ 각 sample-app별 Workflow 작성 (`.github/workflows/`)
   - 변경된 서비스만 빌드되도록 경로 필터 설정
   - 이미지 태그: `git commit SHA` 사용
3. ✅ CI가 infra/ 매니페스트의 이미지 태그를 자동으로 업데이트하는 커밋 추가
4. ✅ ArgoCD 자동 배포 end-to-end 확인

### 완료 기준

- `sample-apps/order-service/` 코드 변경 후 push 시 GitHub Actions가 자동 실행된다. ✅
- GHCR에 새 이미지가 push되고, ArgoCD가 자동으로 클러스터에 배포한다. ✅

### 구현 메모

- GHCR 인증: 별도 Secret 불필요. Workflow 내 `permissions: packages: write`로 `GITHUB_TOKEN` 사용
- 이미지 레지스트리: Docker Hub(`jaehoon9875/`) → GHCR(`ghcr.io/jaehoon9875/`) 전환
- 경로 필터로 변경된 서비스만 빌드 (`sample-apps/{service}/**`)
- bot 커밋은 `infra/manifests/` 경로라 paths 필터 불일치 → Workflow 재트리거 없음 (무한 루프 방지)
- ArgoCD `directory.recurse: true` 설정 필요 — 미설정 시 하위 디렉토리 매니페스트 미탐색
- 동시 빌드 시 bot push 충돌 방지: `git pull --rebase` 추가
- 싱글노드 CPU 부족으로 롤링 업데이트 실패 → 3개 서비스 모두 `strategy: Recreate`로 변경

---

## 7단계: custom-exporter 개발

### 목표

Prometheus가 기본으로 수집하지 않는 메트릭을 수집하는 독립 프로그램을 만든다.

### 할 일

1. ✅ Kafka Consumer Lag Exporter 개발 (Java)
  - Kafka Admin Client로 Consumer Group의 Lag을 조회
  - `/actuator/prometheus` 엔드포인트로 노출
2. ✅ Dockerfile + K8s 배포
3. ✅ Prometheus에서 스크랩 확인
4. ✅ Grafana 대시보드에 Kafka Lag 패널 추가

### 완료 기준

- Grafana에서 Kafka Consumer Lag을 실시간으로 확인할 수 있다. ✅

### 구현 메모

- `custom-exporter/`: Spring Boot + Kafka AdminClient + Micrometer 기반 독립 Pod
- 수집 메트릭: `kafka_consumer_group_lag{group, topic, partition}` — 15초 주기 갱신
- ArgoCD Application: `infra/argocd/obs-apps/kafka-lag-exporter.yaml` (최초 1회 수동 `kubectl apply` 필요)
- CI: `.github/workflows/kafka-lag-exporter.yaml` — `custom-exporter/**` 변경 시 자동 빌드/배포
- **OOM 트러블슈팅**: 기동 시 Exit Code 137 (SIGKILL) 발생
  - 원인: JVM이 컨테이너 메모리 제한 미인식 + 메모리 한도 부족
  - 조치: Dockerfile에 `-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:TieredStopAtLevel=1` 추가
  - 조치: 메모리 `128Mi/256Mi` → `256Mi/512Mi`, startupProbe initialDelaySeconds `20s` → `60s`
- **메트릭 미노출 원인**: Consumer가 오프셋을 한 번도 커밋하지 않으면 Gauge가 등록되지 않음
  - 조치: order-service에 주문 생성 → notification-service가 메시지 소비 후 오프셋 커밋 → 메트릭 노출

---

## 8단계: scripts/ 자동화 도구

### 목표

장애 발생 시 반복되는 수작업을 자동화하여 초기 대응 속도를 높인다.

### 할 일

1. ✅ incident-collector.sh — 장애 시 Pod 로그, describe, 이벤트를 자동 수집
   - 네임스페이스 지정 또는 전체 대상
   - `kubectl logs`, `kubectl describe pod`, `kubectl get events` 수집
   - `kubectl top pod`으로 리소스 사용량 수집
   - 타임스탬프 기반 디렉토리에 결과 저장 (예: `incident-20260330-1200/`)

### 완료 기준

- 스크립트 실행 한 번으로 장애 진단에 필요한 정보가 디렉토리에 저장된다. ✅

### 추후 확장 (필요 시)

- AlertManager webhook 연동으로 장애 감지 시 자동 실행
- Slack 알림에 수집 결과 요약 첨부
- 기타 반복되는 운영 작업이 생기면 그때 추가

---

## 9단계: 전체 리뷰 및 개선

### 목표

1단계~8단계에서 미해결로 남긴 이슈들을 점검하고, 프로젝트 완성도를 높인다.

### 미해결 이슈

미해결 이슈 및 개선 검토 항목은 [docs/ISSUES.md](ISSUES.md)에서 관리한다.

| 이슈 | 관련 단계 |
|------|----------|
| mysql-operator CRD OutOfSync | 5단계 |
| prometheus-stack Grafana SSA 충돌 | 5단계 |
| ArgoCD PreSync Hook Job hang 반복 | 5단계 |
| Log-Trace Correlation — Tempo 링크 No data | 2단계 |
| 싱글노드 CPU 리소스 부족 | 9단계 |
| 부하 테스트 후속 작업 | 4단계 |
| Grafana datasource 관리 구조 개선 | - |
| ~~Metric-Trace Correlation (Exemplar → Tempo) 구축~~ | ~~9단계~~ ✅ |

---

## 각 단계 완료 후 할 일

- 해당 단계의 코드를 커밋하고 push한다.
- 블로그에 해당 단계에서 겪은 문제 해결 과정을 기록한다.
- README.md의 블로그 링크 섹션을 업데이트한다.

