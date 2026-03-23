# CLAUDE.md — infra

## 이 디렉토리의 역할

Kubernetes 클러스터에 배포되는 모든 리소스를 코드로 관리하는 GitOps 디렉토리.
ArgoCD가 이 디렉토리를 감시하며, Git push 시 클러스터에 자동 동기화된다.

## 환경 정보

- 싱글노드 Kubernetes 클러스터 (kubeadm)
- 네임스페이스 구성:
  - `monitoring` — Prometheus, Grafana, Loki, Tempo, Alloy
  - `argocd` — ArgoCD
  - `observability-platform` — sample-apps(order, payment, notification) 및 의존 오픈소스(MySQL, Redis, Kafka) 배포

## 디렉토리 구조

```
infra/
├── argocd/              → ArgoCD Application 정의 (어떤 디렉토리를 동기화할지)
├── prometheus-stack/    → kube-prometheus-stack Helm values.yaml
├── loki/                → Loki Helm values.yaml
├── tempo/               → Tempo Helm values.yaml
└── sample-apps/         → Deployment, Service, ConfigMap 등 K8s 매니페스트
```

## YAML 작성 규칙

- 들여쓰기는 항상 2칸.
- Helm values.yaml 수정 시 기존 주석은 유지한다.
- K8s 매니페스트에는 `app.kubernetes.io/name`, `app.kubernetes.io/part-of` 라벨을 반드시 포함한다.
- 리소스 요청/제한(requests/limits)을 모든 컨테이너에 명시한다.

## ArgoCD 연동 방식

- 각 ArgoCD Application은 이 레포의 특정 하위 경로를 source로 지정한다.
- 동기화 정책: 자동 동기화 (auto-sync) + 자동 프루닝 (auto-prune)
- 예시: `infra/prometheus-stack/` → monitoring 네임스페이스에 동기화

## 수정 시 주의사항

- values.yaml 수정 후에는 `helm template` 명령으로 렌더링 결과를 검증한다.
- PVC가 포함된 리소스를 삭제할 때는 데이터 유실에 주의한다.
- CRD(Custom Resource Definition)는 ArgoCD 동기화 전에 먼저 수동 설치해야 할 수 있다.
