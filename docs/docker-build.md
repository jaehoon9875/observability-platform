# Docker 멀티아키텍처 이미지 빌드 가이드

M1 Mac(ARM64)에서 개발하고 Linux 서버(AMD64)에 배포하는 환경에서
아키텍처 불일치 문제 없이 Docker 이미지를 빌드하고 푸시하는 방법을 정리한다.

---

## 배경: 왜 멀티아키텍처 빌드가 필요한가

| 환경 | 아키텍처 |
| --- | --- |
| 개발 머신 (M1 Mac) | `linux/arm64` |
| 배포 서버 (Linux) | `linux/amd64` |

`docker build`를 그냥 실행하면 빌드 머신의 아키텍처(ARM64) 이미지가 생성된다.
이를 AMD64 서버에서 실행하면 `exec format error`가 발생한다.

`docker buildx`의 멀티플랫폼 빌드를 사용하면 하나의 명령으로 두 아키텍처를 동시에 빌드해 푸시할 수 있다.

---

## Dockerfile 패턴

빌드 스테이지에 `--platform=$BUILDPLATFORM`을 명시한다.
Java 바이트코드는 플랫폼 독립적이므로, 빌드는 네이티브(M1)로 실행해 속도를 유지하고
런타임 스테이지만 타겟 플랫폼용 JRE를 사용한다.

```dockerfile
# 빌드는 항상 네이티브 플랫폼에서 실행 (M1에서 빠르게 컴파일)
FROM --platform=$BUILDPLATFORM eclipse-temurin:17-jdk AS build
...

# 런타임은 타겟 플랫폼 JRE 자동 선택 (amd64 또는 arm64)
FROM eclipse-temurin:17-jre
...
```

---

## 최초 1회: buildx 빌더 생성

```bash
docker buildx create --name multiarch --use
docker buildx inspect --bootstrap
```

> 이미 `multiarch` 빌더가 있다면 `docker buildx use multiarch`만 실행한다.

---

## 이미지 빌드 & 푸시

`--push` 플래그를 사용하면 빌드와 Docker Hub 푸시가 동시에 이루어진다.
(멀티아키텍처 이미지는 로컬에 저장할 수 없으므로 `--push` 또는 `--load` 중 하나를 선택해야 한다.)

### payment-service

```bash
cd sample-apps/payment-service
docker buildx build \
  --platform linux/amd64,linux/arm64 \
  -t jaehoon9875/payment-service:latest \
  --push .
```

### order-service

```bash
cd sample-apps/order-service
docker buildx build \
  --platform linux/amd64,linux/arm64 \
  -t jaehoon9875/order-service:latest \
  --push .
```

---

## 빌드 결과 확인

Docker Hub에서 이미지가 두 아키텍처로 등록됐는지 확인한다.

```bash
docker buildx imagetools inspect jaehoon9875/payment-service:latest
```

출력 예시:
```
Manifests:
  linux/amd64  ...
  linux/arm64  ...
```

---

## 새 서비스 추가 시 체크리스트

1. `Dockerfile` 빌드 스테이지에 `--platform=$BUILDPLATFORM` 추가 확인
2. 위 빌드 명령에 새 서비스 항목 추가
3. `deployment.yaml`의 `image` 태그 확인
