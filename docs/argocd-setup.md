# ArgoCD 설치 가이드

## 개요

ArgoCD를 Helm으로 클러스터에 설치하고, `infra/argocd/`의 Application 매니페스트를 등록하여
GitOps 동기화를 활성화한다.

## 사전 조건

- Kubernetes 클러스터 접근 가능 (`kubectl get nodes` 정상)
- Helm 설치됨 (`helm version`)
- GitHub repo에 접근 가능한 Personal Access Token (private repo인 경우)

---

## 1. ArgoCD 설치

### Helm repo 추가

```bash
helm repo add argo https://argoproj.github.io/argo-helm
helm repo update

# 설치 가능한 버전 확인
helm search repo argo/argo-cd
```

### 설치 명령

```bash
# values.yaml 먼저 받기 (참고용)
helm show values argo/argo-cd --version <VERSION> > infra/helm/argocd/values.yaml

# 설치
helm install argocd argo/argo-cd \
  --version <VERSION> \
  --namespace argocd \
  --create-namespace \
  -f infra/helm/argocd/custom-values.yaml
```

> `custom-values.yaml`의 chart version 주석을 실제 설치 버전으로 업데이트한다.

### 설치 확인

```bash
kubectl get pods -n argocd
# 아래 파드들이 모두 Running 상태여야 함
# argocd-application-controller-*
# argocd-server-*
# argocd-repo-server-*
# argocd-redis-*
# argocd-applicationset-controller-*
```

---

## 2. ArgoCD UI 접근

### Cloudflare Tunnel로 접근 (기본 방식)

cloudflared가 클러스터 내 Pod로 실행 중이며, argocd-server(ClusterIP)로 직접 라우팅.
별도 포트 노출 없이 Cloudflare 도메인으로 접근 가능.

### 포트포워딩으로 접근 (로컬 임시 접근)

```bash
kubectl port-forward svc/argocd-server -n argocd 8080:80
# http://localhost:8080
```

### 초기 admin 비밀번호 확인

```bash
kubectl get secret argocd-initial-admin-secret \
  -n argocd \
  -o jsonpath="{.data.password}" | base64 -d && echo
```

로그인 후 비밀번호를 변경한다 (Settings → Account → Update Password).

---

## 3. GitHub 리포지토리 연결

ArgoCD가 이 repo를 감시할 수 있도록 연결한다.

### CLI 방식

```bash
# argocd CLI 설치 (필요 시)
# https://argo-cd.readthedocs.io/en/stable/cli_installation/

argocd login localhost:8080 --username admin --password <PASSWORD> --insecure

# 리포지토리 추가 (public repo)
argocd repo add https://github.com/jaehoon9875/observability-platform

# private repo인 경우 PAT 사용
argocd repo add https://github.com/jaehoon9875/observability-platform \
  --username jaehoon9875 \
  --password <GITHUB_PAT>
```

### UI 방식

Settings → Repositories → Connect Repo → HTTPS 방식으로 입력

---

## 4. Application 등록

`infra/argocd/` 하위의 매니페스트를 적용하여 각 Application을 등록한다.

### 등록 순서

Operator → 해당 Operator가 관리하는 CR 순서로 적용해야 한다.

```bash
# 1. monitoring 스택
kubectl apply -f infra/argocd/monitoring/

# 2. obs-apps — Operator 먼저
kubectl apply -f infra/argocd/obs-apps/mysql-operator.yaml
kubectl apply -f infra/argocd/obs-apps/strimzi-operator.yaml

# Operator Pod가 Running 상태가 된 후 진행
kubectl get pods -n obs-apps -w

# 3. obs-apps — 나머지
kubectl apply -f infra/argocd/obs-apps/mysql.yaml
kubectl apply -f infra/argocd/obs-apps/kafka.yaml
kubectl apply -f infra/argocd/obs-apps/redis.yaml
kubectl apply -f infra/argocd/obs-apps/kafka-ui.yaml
kubectl apply -f infra/argocd/obs-apps/sample-apps.yaml
```

> **주의**: sample-apps 배포 전 DB Secret을 수동으로 먼저 생성해야 한다.
> ```bash
> kubectl create secret generic order-service-secret \
>   --from-literal=DB_USERNAME=root \
>   --from-literal=DB_PASSWORD=<password> \
>   -n obs-apps
>
> kubectl create secret generic payment-service-secret \
>   --from-literal=DB_USERNAME=root \
>   --from-literal=DB_PASSWORD=<password> \
>   -n obs-apps
> ```

---

## 5. 동기화 확인

### ArgoCD UI

모든 Application이 **Synced** + **Healthy** 상태인지 확인한다.

### CLI

```bash
argocd app list

# 특정 앱 상태 확인
argocd app get loki
argocd app get sample-apps
```

### 동기화 강제 실행 (필요 시)

```bash
argocd app sync <앱이름>

# 전체 동기화
argocd app sync --selector app.kubernetes.io/part-of=observability-platform
```

---

## 6. GitOps 동작 확인

정상 동작 시 아래 흐름이 자동화된다.

```
infra/ 파일 수정 → git push → ArgoCD 감지(~3분) → 클러스터 자동 sync
```

수동으로 즉시 확인하려면:

```bash
# ArgoCD polling 주기 기본값: 3분
# 즉시 적용하려면 UI에서 Sync 또는 CLI로 강제 sync
argocd app sync sample-apps
```

---

## 알려진 이슈

### multiple sources 기능 요구 버전

Helm Application에서 chart registry + git values 파일을 분리하는 방식은 ArgoCD **v2.6 이상**에서만 동작한다.
`helm search repo argo/argo-cd`로 확인한 chart 버전이 ArgoCD v2.6+ 앱 버전에 매핑되는지 확인한다.

### CRD 동기화 실패 (kube-prometheus-stack)

kube-prometheus-stack의 CRD 크기가 커서 기본 `kubectl apply` 방식으로 실패할 수 있다.
`prometheus-stack.yaml`에 `ServerSideApply=true`가 설정되어 있으므로 별도 조치 불필요.

### Operator 의존성 순서

| Application | 선행 조건 |
|---|---|
| mysql | mysql-operator Running 상태 |
| kafka | strimzi-operator Running 상태 |
| kafka-ui | kafka Running 상태 |
| sample-apps | DB Secret 수동 생성 |
