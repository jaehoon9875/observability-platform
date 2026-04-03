# CLAUDE.md — infra

## 이 디렉토리의 역할

Kubernetes 클러스터에 배포되는 모든 리소스를 코드로 관리하는 GitOps 디렉토리.
ArgoCD가 이 디렉토리를 감시하며, Git push 시 클러스터에 자동 동기화된다.

## 환경 정보

- 싱글노드 Kubernetes 클러스터 (kubeadm)
- 네임스페이스 구성:
  - `monitoring` — Prometheus, Grafana, Loki, Tempo, Alloy
  - `argocd` — ArgoCD
  - `obs-apps` — sample-apps 및 의존 오픈소스 배포
    - order-service, payment-service, notification-service
    - MySQL, Kafka, Redis

## 디렉토리 구조

```
infra/
├── argocd/              → ArgoCD Application 정의 (어떤 디렉토리를 동기화할지)
│
├── helm/                → Helm chart로 관리하는 스택
│   ├── kube-prometheus-stack/ → kube-prometheus-stack
│   │   ├── values.yaml       # helm show values로 받은 전체 기본값 (참고용)
│   │   └── custom-values.yaml # 우리 환경에 맞게 오버라이드한 값만 작성
│   ├── loki/
│   │   ├── values.yaml
│   │   └── custom-values.yaml
│   ├── tempo/
│   │   ├── values.yaml
│   │   └── custom-values.yaml
│   ├── alloy/
│   │   ├── values.yaml
│   │   └── custom-values.yaml
│   ├── strimzi-operator/
│   │   ├── values.yaml
│   │   └── custom-values.yaml
│   ├── mysql-operator/
│   │   ├── values.yaml
│   │   └── custom-values.yaml
│   ├── kafka-ui/
│   │   ├── values.yaml
│   │   └── custom-values.yaml
│   └── redis/
│       ├── values.yaml
│       └── custom-values.yaml
│
└── manifests/           → kubectl apply 또는 ArgoCD가 직접 적용하는 raw K8s 매니페스트
    ├── kafka/            → Kafka Operator CRD (Kafka, KafkaTopic)
    ├── mysql/            → MySQL Operator InnoDBCluster
    ├── sample-apps/      → Deployment, Service, ConfigMap, ServiceMonitor
    │   ├── order-service/
    │   ├── payment-service/
    │   └── notification-service/
    └── k6/               → k6 부하 테스트 Job, ConfigMap
```

## values.yaml 작성 규칙

Helm chart 설정은 두 파일로 분리하여 관리한다.

| 파일 | 내용 | 작성 방법 |
|---|---|---|
| `values.yaml` | chart의 전체 기본값 | `helm show values <chart> > values.yaml` |
| `custom-values.yaml` | 우리 환경에 맞게 오버라이드한 값만 | 필요한 키만 직접 작성 |

### Helm 적용 명령

```bash
helm upgrade --install <release> <chart> \
  -f infra/helm/<stack>/values.yaml \
  -f infra/helm/<stack>/custom-values.yaml \
  -n <namespace>
# 뒤에 오는 파일이 앞 파일을 덮어씁니다
```

### custom-values.yaml 작성 원칙

- 전체 기본값을 복사하지 않는다. 의도적으로 변경한 값만 작성한다.
- 파일만 봐도 "우리가 무엇을 바꿨는지"가 명확해야 한다.
- Chart 버전 업그레이드 시 새 기본값을 자연스럽게 흡수할 수 있도록 한다.

```yaml
# 좋은 예 — 변경 의도가 명확
replicaCount: 1

resources:
  requests:
    cpu: 200m
    memory: 256Mi

persistence:
  enabled: true
  size: 5Gi
```

## YAML 작성 규칙

- 들여쓰기는 항상 2칸.
- Helm values.yaml 수정 시 기존 주석은 유지한다.
- K8s 매니페스트에는 `app.kubernetes.io/name`, `app.kubernetes.io/part-of` 라벨을 반드시 포함한다.
- 리소스 요청/제한(requests/limits)을 모든 컨테이너에 명시한다.

## ArgoCD 연동 방식

- 각 ArgoCD Application 매니페스트는 `infra/argocd/` 하위에 위치한다.
- Application 오브젝트 자체는 반드시 `namespace: argocd`에 생성해야 ArgoCD가 감지한다.
- 동기화 정책: 자동 동기화 (auto-sync) + 자동 프루닝 (auto-prune) + selfHeal
- 설치 가이드: `docs/argocd-setup.md` 참조

### ArgoCD Application 구조

```
infra/argocd/
├── monitoring/       # Prometheus, Loki, Tempo, Alloy
└── obs-apps/         # MySQL, Kafka, Redis, sample-apps 등
```

### Application 방식 — manifests (raw K8s)

```yaml
spec:
  source:
    repoURL: https://github.com/jaehoon9875/observability-platform
    path: infra/manifests/sample-apps   # git 경로
    targetRevision: main
  destination:
    namespace: obs-apps                 # 배포 대상 네임스페이스
```

### Application 방식 — Helm (multiple sources, ArgoCD v2.6+)

chart registry와 git values 파일을 분리하는 방식. 버전 고정 및 파일 추적이 명확해진다.

```yaml
spec:
  sources:
    - repoURL: https://grafana.github.io/helm-charts
      chart: loki
      targetRevision: 6.55.0            # chart 버전 고정
      helm:
        valueFiles:
          - $values/infra/helm/loki/custom-values.yaml
    - repoURL: https://github.com/jaehoon9875/observability-platform
      targetRevision: main
      ref: values                       # 위 $values 참조용
  destination:
    namespace: monitoring
```

### Operator 의존성 순서

```
mysql-operator → mysql (InnoDBCluster)
strimzi-operator → kafka (Kafka CR)
kafka → kafka-ui
```

### 주의사항

- kube-prometheus-stack: CRD 크기로 인해 `ServerSideApply=true` 필수
- sample-apps: DB Secret(`order-service-secret`, `payment-service-secret`)은 ArgoCD 관리 밖에서 수동 생성
- k6: on-demand Job이므로 ArgoCD 관리 제외 — `kubectl apply`로 수동 실행

## MySQL 운영 참고

상세 내용은 [docs/mysql-setup.md](../docs/mysql-setup.md) 참조.

- 파드명 직접 지정 필요: `mysql-cluster-0` (레이블 셀렉터로 조회 시 items 비어있음)
- `kubectl exec` 시 반드시 `-c mysql` 컨테이너 명시
- 현재 생성된 DB: `orderdb` (order-service), `paymentdb` (payment-service)

## 수정 시 주의사항

- custom-values.yaml 수정 후에는 `helm template` 명령으로 렌더링 결과를 검증한다.
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
