# 실행 계획

이 문서는 observability-platform 프로젝트의 단계별 실행 계획이다.
각 단계는 순서대로 진행하며, 이전 단계가 완료된 후 다음 단계를 시작한다.

---

## 1단계: order-service 개발 및 배포 ✅

Spring Boot 주문 API + 커스텀 메트릭(Micrometer) + K8s 배포 + Prometheus/Grafana 연동 완료.
`local` 프로파일로 H2 인메모리 DB 사용 가능. DB 인증은 K8s Secret으로 분리 관리.

---

## 2단계: payment-service, notification-service 개발 ✅

서비스 간 REST 통신 + Kafka 비동기 이벤트 처리 + OpenTelemetry 분산 트레이싱 연동 완료.
order → payment → notification 전체 흐름에서 Tempo 트레이스 확인.

---

## 3단계: 대시보드 및 Alert Rule 설계 ✅

SLO 현황 대시보드(가용성·에러 버짓·P99) + JVM 분석 대시보드 설계 및 JSON 저장.
PrometheusRule CRD로 Alert Rule 작성 및 클러스터 적용 완료.

---

## 4단계: k6 부하 테스트 ✅ (일부 홀딩)

정상 트래픽(order-flow.js) + 급증(spike-test.js) 시나리오 작성 및 실행 완료.
대시보드/Alert Rule 트리거 검증 확인. 병목 분석 문서화·k6→Prometheus 연동은 홀딩.

---

## 5단계: infra/ GitOps 전환 (ArgoCD) ✅ (대부분 완료)

Observability 스택 + sample-apps 전체를 ArgoCD 기반 GitOps로 전환 완료.
Synced: alloy, kafka, loki, mysql, redis, sample-apps, strimzi-operator, tempo 등.
미해결: mysql-operator CRD OutOfSync, prometheus-stack Grafana SSA 충돌 → [ISSUES.md](ISSUES.md) 참조.

---

## 6단계: GitHub Actions CI 파이프라인 ✅

코드 커밋 → GitHub Actions 빌드/GHCR push → infra/ 이미지 태그 자동 업데이트 → ArgoCD 자동 배포 end-to-end 완료.
경로 필터로 변경된 서비스만 빌드. bot 커밋 무한 루프 방지 처리.

---

## 7단계: custom-exporter 개발 ✅

Kafka Consumer Lag Exporter를 Java(Spring Boot + Micrometer)로 직접 개발 및 배포 완료.
수집 메트릭: `kafka_consumer_group_lag{group, topic, partition}` — Grafana 대시보드 패널 추가.

---

## 8단계: scripts/ 자동화 도구 ✅

`incident-collector.sh` 작성 완료 — 장애 시 Pod 로그/describe/events/top을 타임스탬프 디렉토리에 자동 수집.
상세 사용법: [docs/incident-collector-guide.md](incident-collector-guide.md).

---

## 9단계: 전체 리뷰 및 개선

### 목표

1단계~8단계에서 미해결로 남긴 이슈들을 점검하고, 프로젝트 완성도를 높인다.

### 미해결 이슈

미해결 이슈 및 개선 검토 항목은 [docs/ISSUES.md](ISSUES.md)에서 관리한다.

| 이슈 | 관련 단계 | 상태 |
|------|----------|------|
| ~~mysql-operator CRD OutOfSync~~ | ~~5단계~~ | ✅ |
| ~~prometheus-stack Grafana SSA 충돌~~ | ~~5단계~~ | ✅ |
| ~~ArgoCD PreSync Hook Job hang 반복~~ | ~~5단계~~ | ✅ |
| ~~Log-Trace Correlation — Tempo 링크 No data~~ | ~~2단계~~ | ✅ |
| ~~싱글노드 CPU 리소스 부족~~ | ~~9단계~~ | ✅ |
| ~~부하 테스트 후속 작업~~ | ~~4단계~~ | ✅ |
| ~~Grafana datasource 관리 구조 개선~~ | ~~-~~ | ✅ |
| ~~Metric-Trace Correlation (Exemplar → Tempo) 구축~~ | ~~9단계~~ | ✅ |

### infra 리팩토링 백로그 (신규)

`infra/` 하위 설정/아키텍처 개선 항목은
[docs/infra-refactoring-backlog.md](infra-refactoring-backlog.md)에서 관리한다.
우선순위(상/중/하)와 상태(TODO/DOING/DONE) 기준으로 기능 1개당 1브랜치 + 1PR로 진행한다.
