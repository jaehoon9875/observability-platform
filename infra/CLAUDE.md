# CLAUDE.md — infra

## 이 디렉토리의 역할

Kubernetes 클러스터에 배포되는 모든 리소스를 코드로 관리하는 GitOps 디렉토리.
ArgoCD가 이 디렉토리를 감시하며, Git push 시 클러스터에 자동 동기화된다.

## 환경 정보

- 싱글노드 Kubernetes 클러스터 (kubeadm)
- 네임스페이스 구성:
  - `monitoring` — Prometheus, Grafana, Loki, Tempo, Alloy
  - `argocd` — ArgoCD
  - `observability-platform` — sample-apps 및 의존 오픈소스 배포
    - order-service, payment-service, notification-service
    - MySQL, Kafka, Redis

## 디렉토리 구조

```
infra/
├── argocd/              → ArgoCD Application 정의 (어떤 디렉토리를 동기화할지)
├── prometheus-stack/    → kube-prometheus-stack Helm values.yaml
├── loki/                → Loki Helm values.yaml
├── tempo/               → Tempo Helm values.yaml
├── mysql/               → MySQL K8s 매니페스트 (mysql.yaml) [TODO: Helm으로 전환 예정]
├── kafka/               → Kafka K8s 매니페스트 (kafka.yaml, kafka-cluster.yaml) [TODO: Helm으로 전환 예정]
├── redis/               → Redis Helm values.yaml
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

### Kafka ArgoCD Application 예시

현재는 `kubectl apply`로 수동 배포 중이며, 추후 ArgoCD Application으로 전환 예정.
`infra/kafka/` 경로를 Application으로 등록하면 별도 코드 변경 없이 GitOps 방식으로 전환 가능.

```yaml
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: kafka
  namespace: argocd
spec:
  source:
    repoURL: https://github.com/your-id/your-repo
    path: infra/kafka
    targetRevision: main
  destination:
    server: https://kubernetes.default.svc
    namespace: observability-platform
  syncPolicy:
    automated:
      prune: true       # Git에서 삭제하면 클러스터에서도 삭제
      selfHeal: true    # 클러스터 상태가 Git과 다르면 자동 복구
```

## TODO

- [ ] MySQL: 현재 K8s 매니페스트(`mysql.yaml`)로 임시 배포 중. 추후 Helm chart 기반으로 전환 예정
- [ ] Kafka: 현재 K8s 매니페스트(`kafka.yaml`, `kafka-cluster.yaml`)로 임시 배포 중. 추후 Helm chart 기반으로 전환 예정

## 수정 시 주의사항

- values.yaml 수정 후에는 `helm template` 명령으로 렌더링 결과를 검증한다.
- PVC가 포함된 리소스를 삭제할 때는 데이터 유실에 주의한다.
- CRD(Custom Resource Definition)는 ArgoCD 동기화 전에 먼저 수동 설치해야 할 수 있다.

## 알려진 이슈

### Strimzi 0.51.0 — user-operator CrashLoopBackOff

**증상**: `kafka-entity-operator` 파드 내 `user-operator` 컨테이너가 CrashLoopBackOff 상태.

**원인**: Strimzi 0.51.0이 entity-operator Deployment를 생성할 때 `user-operator` 컨테이너에
`STRIMZI_SECURITY_PROTOCOL: SSL`과 클러스터 CA 인증서 마운트를 누락하는 버그.
Strimzi 내부 리스너(포트 9091)는 항상 SSL만 허용하므로 user-operator가 PLAINTEXT로
연결을 시도하다 블로킹 → liveness probe(10s delay + 3회 실패)에 의해 반복 재시작됨.
topic-operator는 정상 동작(SSL 설정 정상 주입됨).

**참고**: `apiVersion: kafka.strimzi.io/v1`은 올바른 버전. `v1beta2`는 구버전이며
현재 클러스터에서 두 버전 모두 서빙 중.

**조치**: `kafka-cluster.yaml`의 `entityOperator`에서 `userOperator` 섹션 제거.
현재 `KafkaUser` 리소스를 사용하지 않으므로 기능 영향 없음.
KafkaUser 관리가 필요해지면 Strimzi 버전 업그레이드 후 재활성화 필요.
