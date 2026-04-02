# 리소스 사이징 분석 및 최적화 가이드

## 배경

싱글노드 클러스터 환경에서 CPU requests 합계가 3810m/4000m(95%)에 달해 payment-service Pod가 Pending 상태이고 k6 부하 테스트 등 추가 Pod 스케줄링이 불가한 상태.
단순한 수치 조정이 아닌, 실제 사용량 분석을 바탕으로 근거 있는 리소스 설정을 도출하는 것이 목표.

---

## 1단계: 현황 분석 (2026-04-02)

### 전체 Pod 리소스 현황

`kubectl describe node jh` 결과 기준 주요 Pod:

| Namespace | Pod | CPU Request | Memory Request |
|-----------|-----|------------|---------------|
| monitoring | loki-chunks-cache-0 | 500m (12%) | **9830Mi (62%)** |
| monitoring | loki-results-cache-0 | 500m (12%) | 1229Mi (7%) |
| obs-apps | order-service | 250m (6%) | 256Mi |
| obs-apps | notification-service | 250m (6%) | 256Mi |
| obs-apps | payment-service | 250m (6%) | 256Mi — **Pending** |
| obs-apps | strimzi-cluster-operator | 200m (5%) | 384Mi |
| argocd | argocd-application-controller | 250m (6%) | 256Mi |
| kube-system | kube-apiserver | 250m (6%) | 0 |

**조정 전 전체 합계:**

| Resource | Requests | Allocatable | 사용률 |
|----------|---------|------------|--------|
| CPU | 3810m | 4000m | **95%** |
| Memory | 14693Mi | 16010Mi | **93%** |

### 과잉 프로비저닝 식별 결과

**loki-chunks-cache / loki-results-cache (memcached)**가 주범으로 식별됨.

- `values.yaml` 기본값: `chunksCache.allocatedCPU: 500m`, `allocatedMemory: 8192MB`
- 싱글노드 개발 환경에서 memcached 2개가 CPU 25%, 메모리 69%를 점유 — 명백히 과도
- 특히 chunks-cache의 메모리 9830Mi(= 8192MB × 1.2 안전 버퍼)는 단일 Pod 기준 전체 메모리의 62%

---

## 2단계: 서비스별 심층 분석

### Loki memcached (loki-chunks-cache, loki-results-cache)

#### 현재 설정 (조정 전)

| 항목 | chunks-cache | results-cache |
|------|------------|--------------|
| allocatedCPU | 500m | 500m |
| allocatedMemory | 8192MB | 1024MB |
| 실제 Memory Request | 9830Mi (62%) | 1229Mi (7%) |

#### 분석

- `allocatedMemory: 8192`는 프로덕션 대규모 Loki 클러스터 기준 기본값
- 싱글노드 개발 환경에서는 로그 볼륨이 적어 대용량 캐시가 불필요
- CPU 500m는 memcached 특성상 네트워크 I/O 위주이므로 과도

#### 조정 근거

싱글노드 환경에서 memcached는 수십~수백 MB 수준의 캐시로 충분.
CPU도 단순 캐시 연산이므로 100m으로 충분히 동작 가능.

---

## 3단계: 조정 내역 (2026-04-02)

### 변경 파일: `infra/helm/loki/custom-values.yaml`

```yaml
chunksCache:
  allocatedCPU: 100m      # 500m → 100m
  allocatedMemory: 512    # 8192MB → 512MB

resultsCache:
  allocatedCPU: 100m      # 500m → 100m
  allocatedMemory: 256    # 1024MB → 256MB
```

### 전체 CPU/Memory Requests 변화

| 항목 | 변경 전 | 변경 후 |
|------|--------|--------|
| CPU Requests 합계 | 3810m (95%) | 3260m **(81%)** |
| Memory Requests 합계 | 14693Mi (93%) | 4811Mi **(30%)** |
| CPU 여유 | 190m | **740m** |
| loki-chunks-cache 메모리 | 9830Mi | 614Mi |
| loki-results-cache 메모리 | 1229Mi | 307Mi |

---

## 4단계: 검증 결과 (2026-04-02)

### 스케줄링 복구 확인

- **payment-service**: Pending → Running 복구 확인
  - 원인: CPU 여유 190m < 요구 250m → 조정 후 여유 740m으로 스케줄링 성공
- **k6 Job**: 250m 요구 → 740m 여유 확보로 스케줄링 가능 상태

### `kubectl describe node jh` 결과 (조정 후)

```
cpu     3260m (81%)   5150m (128%)
memory  4811Mi (30%)  7981Mi (51%)
```

---

## 결론 및 교훈

### 핵심 원칙

1. **Helm chart 기본값은 프로덕션 기준** — 싱글노드 개발 환경에서는 반드시 custom-values.yaml에서 오버라이드해야 한다.
2. **memcached는 메모리 집약적** — `allocatedMemory` 설정이 실제 Pod Memory Request로 직결(×1.2 안전 버퍼)되므로 값 설정 시 주의.
3. **requests는 스케줄링 기준, 실사용량과 무관** — 실제 CPU가 여유 있어도 requests 합계가 노드 용량을 초과하면 Pod는 Pending이 됨.
4. **과잉 프로비저닝 탐지 방법**: `kubectl describe node` → Allocated resources 섹션에서 requests 비율 확인.
