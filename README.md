# observability-platform

Kubernetes 기반 MSA 환경에서 Observability 플랫폼을 구축하고, 장애를 시뮬레이션하며, 탐지·대응·분석을 자동화하는 프로젝트입니다.

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                      Kubernetes Cluster                         │
│                                                                 │
│  ┌────────────────┐  ┌────────────────┐  ┌──────────────────┐  │
│  │ order-service   │─▶│ payment-service │─▶│ notification-    │  │
│  │ (Java)          │  │ (Java)          │  │ service (Java)   │  │
│  └───────┬────────┘  └───────┬────────┘  └────────┬─────────┘  │
│          │                   │                     │            │
│          ▼                   ▼                     ▼            │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │                  Observability Stack                      │  │
│  │                                                          │  │
│  │  Metrics : Prometheus ──▶ Grafana                        │  │
│  │  Logs    : Alloy ──▶ Loki ──▶ Grafana                    │  │
│  │  Traces  : OpenTelemetry ──▶ Tempo ──▶ Grafana            │  │
│  └──────────────────────────────────────────────────────────┘  │
│                                                                 │
│  ┌───────────────────────┐  ┌──────────────────────────────┐   │
│  │  custom-exporter       │  │  ArgoCD (GitOps)             │   │
│  │  (Kafka Lag 등)        │  │  infra/ 디렉토리 자동 동기화  │   │
│  └───────────────────────┘  └──────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

## 프로젝트 구조

```
observability-platform/
│
├── sample-apps/                  # 테스트 대상 MSA 애플리케이션
│   ├── order-service/            # 주문 API (Spring Boot, Java)
│   ├── payment-service/          # 결제 API (Spring Boot, Java)
│   └── notification-service/     # 알림 API (Spring Boot, Java)
│
├── custom-exporter/              # 직접 개발한 Prometheus Exporter
│   └── kafka-lag-exporter/       # Kafka Consumer Lag 수집기
│
├── infra/                        # IaC + GitOps 매니페스트
│   ├── argocd/                   # ArgoCD Application 정의
│   ├── prometheus-stack/         # kube-prometheus-stack Helm values
│   ├── loki/                     # Loki Helm values
│   ├── tempo/                    # Tempo Helm values
│   └── sample-apps/              # 샘플 앱 K8s 매니페스트
│
├── dashboards/                   # Grafana 대시보드 JSON
│   ├── slo-overview.json         # SLO 현황 대시보드
│   └── jvm-analysis.json         # JVM 성능 분석 대시보드
│
├── alerts/                       # Prometheus Alert Rule 정의
│   ├── slo-alerts.yaml           # SLO 기반 알림 규칙
│   └── infra-alerts.yaml         # 인프라 상태 알림 규칙
│
├── load-tests/                   # k6 부하 테스트 시나리오
│   ├── order-flow.js             # 주문 흐름 부하 테스트
│   └── spike-test.js             # 트래픽 급증 시뮬레이션
│
└── scripts/                      # 운영 자동화 스크립트
    ├── incident-collector.sh     # 장애 시 진단 정보 자동 수집
    └── heap-analyzer.py          # JVM 힙 덤프 분석 도구
```

## 기술 스택


| 영역                   | 기술                                        |
| -------------------- | ----------------------------------------- |
| Language & Framework | Java 17, Spring Boot 3.5.x, JPA/Hibernate |
| Infrastructure       | Kubernetes (kubeadm), ArgoCD              |
| Observability        | Prometheus, Grafana, Loki, Alloy, Tempo   |
| Load Testing         | k6                                        |
| Scripting            | Python, Shell Script                      |
| Database             | MySQL 8.0, Redis, Kafka 3.7.x             |
| CI/CD                | ArgoCD, GitHub Actions                    |


## 각 디렉토리 상세 설명

### sample-apps/

Observability 스택의 관측 대상이 되는 간단한 MSA 애플리케이션입니다.

- 서비스 간 REST API 통신으로 분산 환경을 구성합니다.
- Micrometer를 통해 커스텀 비즈니스 메트릭(주문 처리 시간, 결제 실패율 등)을 노출합니다.
- OpenTelemetry로 분산 트레이싱을 연동합니다.

### custom-exporter/

Prometheus가 기본으로 수집하지 않는 메트릭을 수집하는 독립 프로그램입니다.

- 예: Kafka Consumer Lag, MySQL Slow Query 빈도 등
- `/metrics` 엔드포인트를 통해 Prometheus가 스크랩할 수 있도록 구성합니다.

### infra/

모든 인프라를 코드로 관리합니다 (GitOps).

- ArgoCD가 이 디렉토리를 바라보며, Git push만으로 클러스터 상태가 동기화됩니다.
- Helm chart의 values.yaml을 통해 각 Observability 도구의 설정을 버전 관리합니다.

### dashboards/

직접 설계한 Grafana 대시보드를 JSON으로 관리합니다.

- SLO 현황 대시보드: 서비스별 가용성, 에러 버짓 소진율
- JVM 분석 대시보드: Heap 사용량, GC 빈도, 스레드 상태

### alerts/

SLO 기반의 Alert Rule을 정의합니다.

- 에러율이 SLO 임계치를 초과할 때 알림
- Pod 리소스 사용량 이상 감지

### load-tests/

k6로 작성한 부하 테스트 시나리오입니다.

- 정상 트래픽 시뮬레이션 및 급증(spike) 시나리오
- 테스트 결과를 Prometheus 메트릭으로 내보내 Grafana에서 시각화

### scripts/

반복적인 운영 작업을 자동화하는 스크립트 모음입니다.

- `incident-collector.sh`: 장애 발생 시 Pod 로그, describe, 이벤트를 한 번에 수집
- `heap-analyzer.py`: JVM 힙 덤프를 파싱하여 메모리 누수 의심 객체를 요약

## 실행 환경

- 싱글노드 Kubernetes 클러스터 (kubeadm)
- OS: Linux (홈서버)

## 블로그

각 구성 요소를 구축하면서 겪은 문제 해결 과정과 장애 시뮬레이션 Post-mortem을 블로그에 기록합니다.

- (링크 추가 예정)

