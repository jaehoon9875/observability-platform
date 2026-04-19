# Sealed Secrets 설정 가이드

Grafana admin 비밀번호를 SealedSecret으로 관리하기 위한 초기 설정 절차.

SealedSecret은 암호화된 형태로 Git에 커밋할 수 있는 비밀 관리 방식이다.
`sealed-secrets` 컨트롤러만이 복호화 키를 보유하므로 암호화된 값을 그대로 커밋해도 안전하다.

---

## 구성 요소

| 리소스 | 위치 | 설명 |
|--------|------|------|
| sealed-secrets 컨트롤러 | `kube-system` ns | Helm chart로 ArgoCD 관리 |
| `grafana-admin-secret` SealedSecret | `infra/manifests/monitoring-secrets/` | kubeseal로 생성, Git 커밋 |
| ArgoCD App (sealed-secrets) | `infra/argocd/infra/sealed-secrets.yaml` | 컨트롤러 설치 |
| ArgoCD App (monitoring-secrets) | `infra/argocd/monitoring/monitoring-secrets.yaml` | SealedSecret 배포 |

---

## 최초 설정 절차

### 1. kubeseal CLI 설치

```bash
# Linux amd64
KUBESEAL_VERSION=0.27.3
curl -Lo kubeseal.tar.gz \
  "https://github.com/bitnami-labs/sealed-secrets/releases/download/v${KUBESEAL_VERSION}/kubeseal-${KUBESEAL_VERSION}-linux-amd64.tar.gz"
tar -xzf kubeseal.tar.gz kubeseal
sudo install -m 755 kubeseal /usr/local/bin/kubeseal
kubeseal --version
```

### 2. sealed-secrets 컨트롤러 ArgoCD Application 등록

```bash
kubectl apply -f infra/argocd/infra/sealed-secrets.yaml
```

컨트롤러 기동 확인:

```bash
kubectl get pods -n kube-system -l app.kubernetes.io/name=sealed-secrets
# NAME                              READY   STATUS    RESTARTS   AGE
# sealed-secrets-xxx                1/1     Running   0          1m
```

### 3. SealedSecret 생성

컨트롤러가 Running 상태가 된 후 실행한다.

```bash
kubectl create secret generic grafana-admin-secret \
  -n monitoring \
  --from-literal=admin-user=admin \
  --from-literal=admin-password='rhdid12!@' \
  --dry-run=client -o yaml \
| kubeseal \
  --controller-namespace kube-system \
  --controller-name sealed-secrets \
  --format yaml \
> infra/manifests/monitoring-secrets/grafana-admin-sealedsecret.yaml
```

생성된 파일을 확인한다 (`encryptedData` 필드에 실제 암호화 값이 들어있어야 함):

```bash
cat infra/manifests/monitoring-secrets/grafana-admin-sealedsecret.yaml
```

### 4. monitoring-secrets ArgoCD Application 등록

```bash
kubectl apply -f infra/argocd/monitoring/monitoring-secrets.yaml
```

`grafana-admin-secret`이 `monitoring` 네임스페이스에 생성되었는지 확인:

```bash
kubectl get secret grafana-admin-secret -n monitoring
```

### 5. 변경사항 커밋 & 푸시

```bash
git add infra/manifests/monitoring-secrets/grafana-admin-sealedsecret.yaml
git add infra/argocd/infra/sealed-secrets.yaml
git add infra/argocd/monitoring/monitoring-secrets.yaml
git add infra/helm/kube-prometheus-stack/custom-values.yaml
git commit -m "infra: Sealed Secrets 도입 및 Grafana admin 비밀번호 SealedSecret으로 관리"
git push
```

ArgoCD가 `prometheus-stack`을 자동 sync하면서 Grafana가 `grafana-admin-secret`을 참조하도록 재기동된다.

### 6. Grafana 접속 확인

```bash
# Grafana pod 재기동 확인
kubectl rollout status deployment/my-kube-prometheus-stack-grafana -n monitoring

# 비밀번호 확인 (base64 디코딩)
kubectl get secret grafana-admin-secret -n monitoring \
  -o jsonpath='{.data.admin-password}' | base64 -d
```

Grafana UI(`http://<node-ip>:30300`)에서 `admin` / `rhdid12!@`로 로그인 확인.

---

## 앱 DB Secret GitOps 편입

`order-service`, `payment-service`, `notification-service` 각각의 DB 인증 정보를 SealedSecret으로 관리한다.
컨트롤러와 kubeseal CLI는 이미 설치되어 있으므로 SealedSecret 생성부터 진행한다.

### 사전 확인

기존 수동 생성 Secret 존재 여부 확인:

```bash
kubectl get secret order-service-secret payment-service-secret notification-service-secret -n obs-apps
```

### SealedSecret 생성 (서버에서 실행)

아래 명령어에서 `<DB_USERNAME>`, `<DB_PASSWORD>` 값을 실제 값으로 교체 후 실행한다.
생성된 파일은 repo로 복사해서 커밋한다.

**order-service:**

```bash
kubectl create secret generic order-service-secret \
  -n obs-apps \
  --from-literal=DB_USERNAME=<DB_USERNAME> \
  --from-literal=DB_PASSWORD=<DB_PASSWORD> \
  --dry-run=client -o yaml \
| kubeseal \
  --controller-namespace kube-system \
  --controller-name sealed-secrets \
  --format yaml \
> infra/manifests/sample-apps/order-service/sealedsecret.yaml
```

**payment-service:**

```bash
kubectl create secret generic payment-service-secret \
  -n obs-apps \
  --from-literal=DB_USERNAME=<DB_USERNAME> \
  --from-literal=DB_PASSWORD=<DB_PASSWORD> \
  --dry-run=client -o yaml \
| kubeseal \
  --controller-namespace kube-system \
  --controller-name sealed-secrets \
  --format yaml \
> infra/manifests/sample-apps/payment-service/sealedsecret.yaml
```

**notification-service:**

```bash
kubectl create secret generic notification-service-secret \
  -n obs-apps \
  --from-literal=DB_USERNAME=<DB_USERNAME> \
  --from-literal=DB_PASSWORD=<DB_PASSWORD> \
  --dry-run=client -o yaml \
| kubeseal \
  --controller-namespace kube-system \
  --controller-name sealed-secrets \
  --format yaml \
> infra/manifests/sample-apps/notification-service/sealedsecret.yaml
```

### 커밋 & ArgoCD sync

```bash
git add infra/manifests/sample-apps/order-service/sealedsecret.yaml
git add infra/manifests/sample-apps/payment-service/sealedsecret.yaml
git add infra/manifests/sample-apps/notification-service/sealedsecret.yaml
git commit -m "infra: 앱 DB Secret SealedSecret으로 GitOps 편입"
git push
```

`sample-apps` ArgoCD App이 `infra/manifests/sample-apps`를 `recurse: true`로 관리하므로
별도 ArgoCD Application 등록 없이 자동 sync된다.

### 수동 Secret 제거 및 검증

ArgoCD sync 완료 후 SealedSecret → Secret 자동 생성 확인:

```bash
kubectl get secret order-service-secret payment-service-secret \
  notification-service-secret -n obs-apps
```

SealedSecret이 정상 적용된 것을 확인한 뒤 기존 수동 생성 Secret을 삭제한다.
(SealedSecret 컨트롤러가 이미 Secret을 재생성하므로 삭제 후 자동으로 복원됨)

```bash
# Secret 삭제 → 컨트롤러가 SealedSecret 기반으로 즉시 재생성
kubectl delete secret order-service-secret payment-service-secret \
  notification-service-secret -n obs-apps

# 재생성 확인
kubectl get secret order-service-secret payment-service-secret \
  notification-service-secret -n obs-apps
```

파드가 정상 기동되는지 확인:

```bash
kubectl get pods -n obs-apps
```

---

## 비밀번호 변경 방법

비밀번호를 변경할 때는 kubeseal로 새 SealedSecret을 생성 후 커밋한다.
**Grafana UI에서 직접 변경하면 안 된다** (ArgoCD가 SealedSecret을 다시 적용하면 덮어씌워짐).

```bash
kubectl create secret generic grafana-admin-secret \
  -n monitoring \
  --from-literal=admin-user=admin \
  --from-literal=admin-password='새비밀번호' \
  --dry-run=client -o yaml \
| kubeseal \
  --controller-namespace kube-system \
  --controller-name sealed-secrets \
  --format yaml \
> infra/manifests/monitoring-secrets/grafana-admin-sealedsecret.yaml

git add infra/manifests/monitoring-secrets/grafana-admin-sealedsecret.yaml
git commit -m "infra: Grafana admin 비밀번호 갱신"
git push
```

ArgoCD가 새 SealedSecret을 sync하면 `grafana-admin-secret`이 업데이트되고,
Grafana Pod가 재기동되면서 새 비밀번호가 적용된다.

---

## 주의사항

- `sealed-secrets-key` (복호화 키)는 `kube-system` 네임스페이스에 자동 생성된다.
  클러스터를 재구축할 경우 이 키가 없으면 기존 SealedSecret을 복호화할 수 없으므로,
  키를 별도로 백업해두는 것을 권장한다:
  ```bash
  kubectl get secret -n kube-system -l sealedsecrets.bitnami.com/sealed-secrets-key \
    -o yaml > sealed-secrets-master-key-backup.yaml
  # 이 파일은 절대 Git에 커밋하지 말 것
  ```
- SealedSecret은 네임스페이스 범위로 암호화된다.
  다른 네임스페이스의 컨트롤러로는 복호화할 수 없다.
