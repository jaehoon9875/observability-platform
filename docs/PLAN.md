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
    -n apps
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
5. 각 서비스 Dockerfile + K8s 매니페스트 작성 및 배포

### 완료 기준

- order → payment 호출 시 Tempo에서 분산 트레이스가 보인다.
- Kafka를 통한 비동기 통신이 동작한다.

---

## 3단계: 대시보드 및 Alert Rule 설계

### 목표

SLO 기반 모니터링 체계를 구축한다.

### 할 일

1. SLO 정의
  - 예: order-service 가용성 99.9%, 응답시간 P99 < 500ms
2. Grafana 대시보드 설계 및 JSON 저장
  - SLO 현황 대시보드 (가용성, 에러 버짓 소진율)
  - JVM 분석 대시보드 (Heap, GC, Thread)
  - 서비스 간 통신 대시보드 (호출 성공률, 레이턴시)
3. Prometheus Alert Rule 작성
  - SLO 위반 시 알림
  - Pod 리소스 이상 감지 알림
4. dashboards/ 와 alerts/ 디렉토리에 파일 저장

### 완료 기준

- Grafana 대시보드에서 SLO 현황을 한눈에 볼 수 있다.
- Alert Rule이 Prometheus에 로드되어 있다.

---

## 4단계: k6 부하 테스트

### 목표

트래픽을 시뮬레이션하여 병목 지점을 찾고, 대시보드/알림이 제대로 동작하는지 검증한다.

### 할 일

1. k6 테스트 시나리오 작성
  - 정상 트래픽 시나리오 (order-flow.js)
  - 급증(spike) 시나리오 (spike-test.js)
2. 부하 테스트 실행 후 Grafana에서 메트릭 변화 확인
3. 병목 지점 분석 및 문서화
4. (선택) k6 결과를 Prometheus로 내보내 Grafana에서 시각화

### 완료 기준

- 부하 테스트 중 대시보드에서 트래픽 변화가 실시간으로 보인다.
- Alert Rule이 트리거된다.

---

## 5단계: infra/ GitOps 전환

### 목표

수동으로 설치한 Observability 스택을 ArgoCD 기반 GitOps로 전환한다.

### 할 일

1. ArgoCD 설치 (아직 안 했다면)
2. infra/ 하위에 각 스택의 Helm values.yaml 정리
3. ArgoCD Application 매니페스트 작성
4. Git push → 클러스터 자동 동기화 확인
5. sample-apps도 infra/sample-apps/ 매니페스트로 ArgoCD 관리

### 완료 기준

- infra/ 디렉토리의 values.yaml을 수정하고 push하면 클러스터에 자동 반영된다.

---

## 6단계: custom-exporter 개발

### 목표

Prometheus가 기본으로 수집하지 않는 메트릭을 수집하는 독립 프로그램을 만든다.

### 할 일

1. Kafka Consumer Lag Exporter 개발 (Java 또는 Go)
  - Kafka Admin Client로 Consumer Group의 Lag을 조회
  - `/metrics` 엔드포인트로 노출
2. Dockerfile + K8s 배포
3. Prometheus에서 스크랩 확인
4. Grafana 대시보드에 Kafka Lag 패널 추가

### 완료 기준

- Grafana에서 Kafka Consumer Lag을 실시간으로 확인할 수 있다.

---

## 7단계: scripts/ 자동화 도구

### 할 일

1. incident-collector.sh — 장애 시 Pod 로그, describe, 이벤트를 자동 수집
2. heap-analyzer.py — JVM 힙 덤프를 분석하여 메모리 누수 의심 객체 요약

---

## 각 단계 완료 후 할 일

- 해당 단계의 코드를 커밋하고 push한다.
- 블로그에 해당 단계에서 겪은 문제 해결 과정을 기록한다.
- README.md의 블로그 링크 섹션을 업데이트한다.

