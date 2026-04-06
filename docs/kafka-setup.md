# Kafka on Kubernetes (Strimzi)

Strimzi Operator를 사용하여 Kubernetes 위에 Kafka 클러스터를 구축하는 방법을 정리한 문서입니다.

> **현재 상태**: ArgoCD GitOps로 관리 중 (5단계 완료)

---

## 📁 디렉토리 구조

```
infra/
└── kafka/
    ├── kafka-cluster.yaml   # KafkaNodePool + Kafka 클러스터 정의
    └── kafka-topic.yaml     # KafkaTopic 정의
```

> Kafka UI는 Helm CLI로 직접 설치 (별도 파일 없음 — Step 4 참고)

---

## ⚙️ 사전 조건

- Kubernetes 클러스터 (kubeadm 싱글 노드 또는 그 이상)
- `kubectl` 설치 및 클러스터 연결 확인
- `helm` v3 이상 설치
- StorageClass 설정 완료

```bash
# 클러스터 상태 확인
kubectl get nodes

# StorageClass 확인 (없으면 아래 스토리지 설정 섹션 참고)
kubectl get storageclass
```

---

## 💾 스토리지 설정 (kubeadm 환경)

kubeadm은 기본 StorageClass가 없으므로 `local-path-provisioner`를 설치합니다.
minikube나 cloud 환경이라면 이 단계를 건너뛰세요.

```bash
kubectl apply -f https://raw.githubusercontent.com/rancher/local-path-provisioner/master/deploy/local-path-storage.yaml

# 기본 StorageClass로 설정
kubectl patch storageclass local-path \
  -p '{"metadata":{"annotations":{"storageclass.kubernetes.io/is-default-class":"true"}}}'

# 확인
kubectl get storageclass
```

---

## Step 1. Strimzi Operator 설치

Strimzi Operator를 Helm으로 설치합니다.
Operator는 Kafka 클러스터의 생성/관리/복구를 자동으로 처리하는 컨트롤러입니다.

```bash
# 네임스페이스가 없을 경우 먼저 생성
kubectl create namespace obs-apps

# Helm 레포 추가
helm repo add strimzi https://strimzi.io/charts/
helm repo update

helm install strimzi-operator strimzi/strimzi-kafka-operator \
  --namespace obs-apps \
  --set watchNamespaces="{obs-apps}"
```

### ✅ 설치 확인

```bash
kubectl get pods -n obs-apps

# 아래처럼 Running 상태여야 정상
# strimzi-cluster-operator-xxx   1/1   Running
```

> `watchNamespaces`는 operator가 감시할 네임스페이스를 명시합니다. 미지정 시 기본 동작이 버전마다 다를 수 있으므로 명시적으로 지정하는 것을 권장합니다.

---

## Step 2. Kafka 클러스터 생성

`infra/manifests/kafka/kafka-cluster.yaml`을 적용하여 Kafka 클러스터를 생성합니다.
Strimzi가 CRD(Custom Resource Definition)를 읽고 실제 Pod를 자동으로 생성해줍니다.

> 클러스터 정의 파일: [`infra/manifests/kafka/kafka-cluster.yaml`](../infra/manifests/kafka/kafka-cluster.yaml)
> - `KafkaNodePool`: 노드의 역할, 스토리지, replica 수 정의 (broker + controller 겸임, 싱글 노드용)
> - `Kafka`: 클러스터 전체 설정 (리스너, Kafka 버전 등)

### 적용 및 확인

```bash
kubectl apply -f infra/manifests/kafka/kafka-cluster.yaml

# Pod 상태 확인 (2~3분 소요)
kubectl get pods -n obs-apps -w

# 정상 상태
# kafka-controller-0              1/1   Running
# kafka-entity-operator-xxx       2/2   Running
# strimzi-cluster-operator-xxx    1/1   Running
```

> **ZooKeeper Pod가 없는 게 정상입니다** — KRaft 모드이므로 ZooKeeper가 필요 없습니다.

문제가 발생하면 아래 명령어로 원인을 확인하세요:

```bash
# Pod 상세 정보 (Events 섹션에서 원인 확인)
kubectl describe pod kafka-controller-0 -n obs-apps

# Operator 로그 확인
kubectl logs -l name=strimzi-cluster-operator -n obs-apps
```

---

## Step 3. Kafka Topic 생성

토픽도 CRD로 선언합니다. Strimzi의 entity-operator가 이를 읽고 Kafka 내부에 실제 토픽을 생성해줍니다.

> 토픽 정의 파일: [`infra/manifests/kafka/kafka-topic.yaml`](../infra/manifests/kafka/kafka-topic.yaml)
> - `strimzi.io/cluster` 라벨로 어떤 Kafka 클러스터에 생성할지 지정 (필수)

### 적용 및 확인

```bash
kubectl apply -f infra/manifests/kafka/kafka-topic.yaml

# 토픽 생성 확인 (READY: True 여야 정상)
kubectl get kafkatopic -n obs-apps
```

---

## Step 4. Kafka UI 설치

Kafka UI를 Helm으로 설치합니다.
Kafka UI는 클러스터 내부 리스너(9092)로 Kafka와 통신하므로 별도 외부 설정 없이 연결됩니다.

```bash
helm repo add kafka-ui https://provectus.github.io/kafka-ui-charts
helm repo update

helm install kafka-ui kafka-ui/kafka-ui \
  --version 0.7.6 \
  --namespace obs-apps \
  -f infra/helm/kafka-ui/custom-values.yaml
```

### 확인 및 접속

```bash
# Pod 및 Service 상태 확인
kubectl get pods -n obs-apps
kubectl get svc -n obs-apps

# 서버 IP 확인
kubectl get nodes -o wide
```

브라우저에서 `http://<서버IP>:30080` 으로 접속하면 Kafka UI를 사용할 수 있습니다.

---

## 📌 버전 정보

| 구성 요소 | 버전 |
|---|---|
| Strimzi Operator | latest |
| Apache Kafka | 4.1.0 |
| Kafka UI | latest |

> **⚠️ Strimzi 버전과 Kafka 버전은 반드시 호환되는 조합을 사용해야 합니다.**
> Operator가 지원하지 않는 Kafka 버전을 명시하면 클러스터 생성이 거부됩니다.
> 호환 매트릭스: https://strimzi.io/downloads/