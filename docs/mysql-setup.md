# MySQL on Kubernetes (MySQL Operator)

MySQL Operator for Kubernetes를 사용하여 MySQL InnoDB Cluster를 구축하는 방법을 정리한 문서입니다.

> **참고 문서**: [MySQL Operator for Kubernetes 공식 README](https://github.com/mysql/mysql-operator/blob/trunk/README.md)
> **공식 문서**: https://dev.mysql.com/doc/mysql-operator/en/

> **현재 상태**: kubectl을 사용한 수동 배포
> **도입 예정**: ArgoCD를 통한 GitOps 자동화 (`infra/CLAUDE.md` 참고)

---

## 📁 디렉토리 구조

```
infra/
└── mysql/
    └── mysql-cluster.yaml   # InnoDBCluster 정의 (Secret 제외)
```

> Secret(비밀번호)은 Git에 커밋하지 않고 kubectl로 직접 생성합니다.

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

## Step 1. MySQL Operator 설치

MySQL Operator를 Helm으로 설치합니다.
Operator는 `obs-apps` 네임스페이스에 배포되며, InnoDBCluster 리소스를 감시합니다.

```bash
# 네임스페이스가 없을 경우 먼저 생성
kubectl create namespace obs-apps

# Helm 레포 추가
helm repo add mysql-operator https://mysql.github.io/mysql-operator/
helm repo update

# Operator 설치
helm install mysql-operator mysql-operator/mysql-operator \
  --namespace obs-apps
```

### ✅ 설치 확인

```bash
kubectl get deployment -n obs-apps mysql-operator

# 아래처럼 READY 1/1 상태여야 정상
# NAME             READY   UP-TO-DATE   AVAILABLE   AGE
# mysql-operator   1/1     1            1           ...
```

---

## Step 2. Secret 생성

InnoDBCluster가 참조할 MySQL root 계정 Secret을 생성합니다.
Secret은 Git에 커밋하지 않으므로 kubectl로 직접 생성합니다.

```bash
kubectl create secret generic mysql-root-secret \
  --namespace obs-apps \
  --from-literal=rootUser=root \
  --from-literal=rootHost=% \
  --from-literal=rootPassword="<YOUR_PASSWORD>"
```

> **주의**: `<YOUR_PASSWORD>`를 실제 비밀번호로 교체하세요. Secret은 클러스터에만 존재하며 Git에는 저장되지 않습니다.

---

## Step 3. InnoDBCluster 생성

`infra/mysql/mysql-cluster.yaml`을 적용하여 MySQL InnoDB Cluster를 생성합니다.
MySQL Operator가 CRD를 읽고 StatefulSet, Service 등을 자동으로 생성합니다.

> 클러스터 정의 파일: [`infra/mysql/mysql-cluster.yaml`](../infra/mysql/mysql-cluster.yaml)
> - `secretName`: Step 2에서 생성한 Secret 이름
> - `instances: 1`: 싱글 노드 환경에 맞게 1개로 설정 (운영 환경 권장은 3개 이상)
> - `tlsUseSelfSigned: true`: 자체 서명 TLS 인증서 자동 생성

### 적용 및 확인

```bash
kubectl apply -f infra/mysql/mysql-cluster.yaml

# 클러스터 상태 확인 (5~10분 소요)
kubectl get innodbcluster -n obs-apps --watch

# 정상 상태 (ONLINE, INSTANCES 수 일치)
# NAME           STATUS   ONLINE   INSTANCES   ROUTERS   AGE
# mysql-cluster  ONLINE   1        1           1         5m
```

문제가 발생하면 아래 명령어로 원인을 확인하세요:

```bash
# InnoDBCluster 상세 정보 (Events 섹션 확인)
kubectl describe innodbcluster mysql-cluster -n obs-apps

# Operator 로그 확인
kubectl logs -n obs-apps deployment/mysql-operator
```

---

## Step 4. 접속 확인

### 클러스터 내부 접속 (MySQL Shell)

임시 Pod를 띄워 클러스터 내부에서 MySQL에 접속합니다.

```bash
kubectl run --rm -it myshell \
  --namespace obs-apps \
  --image=container-registry.oracle.com/mysql/community-operator \
  -- mysqlsh

# MySQL Shell 진입 후
MySQL JS> \connect root@mysql-cluster.obs-apps.svc.cluster.local

# 비밀번호 입력 후 접속 확인
MySQL mysql-cluster JS> \sql SELECT version();
```

### 포트 포워딩으로 로컬 접속

```bash
# 포트 포워딩 (읽기/쓰기 포트 6446 → 로컬 3306)
kubectl port-forward service/mysql-cluster -n obs-apps 3306:6446

# 별도 터미널에서 접속
mysql -h127.0.0.1 -P3306 -uroot -p
```

### 애플리케이션에서의 접속 주소

```
호스트: mysql-cluster.obs-apps.svc.cluster.local
포트: 6446 (읽기/쓰기), 6447 (읽기 전용)
```

> MySQL Operator가 생성하는 Service 포트 구성:
> - `3306` — MySQL Protocol (Router 경유, 읽기/쓰기)
> - `6446` — MySQL Protocol 읽기/쓰기
> - `6447` — MySQL Protocol 읽기 전용
> - `33060`, `6448`, `6449` — X Protocol

---

## 📌 버전 정보

| 구성 요소 | 버전 |
|---|---|
| MySQL Operator | latest (Helm) |
| MySQL Server | 8.x (Operator 기본값) |

> **참고**: MySQL Operator 버전과 MySQL Server 버전의 호환 매트릭스는 [공식 문서](https://dev.mysql.com/doc/mysql-operator/en/)를 확인하세요.
