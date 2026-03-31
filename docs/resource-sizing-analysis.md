# 리소스 사이징 분석 및 최적화 가이드 (초안)

## 배경

싱글노드 클러스터 환경에서 CPU requests 합계가 3960m/4000m(99%)에 달해 k6 부하 테스트 등 추가 Pod 스케줄링이 불가한 상태.
단순한 수치 조정이 아닌, 실제 사용량 분석을 바탕으로 근거 있는 리소스 설정을 도출하는 것이 목표.

## 작업 계획

### 1단계: 현황 분석
`kubectl top pods -A`로 실제 CPU/Memory 사용량을 수집하고, 현재 requests/limits와 비교해 과잉 프로비저닝된 Pod를 식별한다.

### 2단계: 서비스별 심층 분석
- **Java 서비스 (order/payment/notification/custom-exporter)**: JVM 힙 설정, GC 동작, `-XX:MaxRAMPercentage` vs 실제 힙 사용량 비교
- **Loki memcached (loki-chunks-cache, loki-results-cache)**: 캐시 히트율, 실제 메모리/CPU 사용량 → 500m CPU 과도 여부 확인
- **Kafka/Zookeeper**: 메시지 처리량 대비 리소스 적정성

### 3단계: 조정 및 검증
- infra/ 값 수정 후 k6 부하 테스트 재실행
- 조정 전후 메트릭 비교로 성능 회귀 없음 확인

### 4단계: 문서화
서비스별 근거 있는 조정 내역, JVM right-sizing 접근법, 부하 테스트 전후 비교 결과를 이 문서에 기록한다.

---

## 1단계: 현황 분석

### 전체 Pod 리소스 현황

> `kubectl top pods -A --sort-by=cpu` 결과

| Namespace | Pod | CPU(cores) | MEMORY(bytes) |
|-----------|-----|-----------|---------------|
| | | | |

> `kubectl top pods -A --sort-by=memory` 결과

| Namespace | Pod | CPU(cores) | MEMORY(bytes) |
|-----------|-----|-----------|---------------|
| | | | |

### 요청(Requests) vs 실제 사용량 비교

> 각 서비스의 requests/limits 대비 실제 사용량

| 서비스 | CPU Request | CPU 실사용 | 사용률 | Memory Request | Memory 실사용 | 사용률 |
|--------|------------|-----------|--------|---------------|-------------|--------|
| | | | | | | |

### 과잉 프로비저닝 식별 결과

> 분석을 통해 식별된 주요 과잉 프로비저닝 Pod 및 원인 요약

---

## 2단계: 서비스별 심층 분석

### Java 서비스

#### JVM 설정 현황

| 서비스 | JVM 플래그 | Heap Request | Heap 실사용 | GC 횟수/분 | GC 일시정지 평균 |
|--------|-----------|-------------|-----------|-----------|---------------|
| order-service | | | | | |
| payment-service | | | | | |
| notification-service | | | | | |
| custom-exporter | | | | | |

#### JVM 분석

> Grafana JVM 대시보드 및 `jcmd`, `jstat` 등을 통한 힙/GC 분석 결과

#### JVM 조정 근거

> 분석 결과를 바탕으로 각 서비스의 `-Xms`, `-Xmx`, `MaxRAMPercentage` 조정 근거

---

### Loki memcached (loki-chunks-cache, loki-results-cache)

#### 현재 설정

```
CPU Request: 500m (각각)
Memory Request: (확인 필요)
```

#### 실제 사용량 분석

> `kubectl top pod` 결과 및 memcached stats 조회 결과

#### 캐시 히트율 분석

> 캐시 히트율 및 싱글노드 환경에서의 적정 리소스 근거

---

### Kafka / Zookeeper

#### 현재 설정

> `kubectl get pod -n obs-infra -o json` 등으로 확인한 requests/limits

#### 실제 사용량 분석

> 메시지 처리량(throughput) 대비 CPU/Memory 사용 패턴

---

## 3단계: 조정 내역

### 변경 사항 요약

| 서비스 | 항목 | 변경 전 | 변경 후 | 근거 |
|--------|------|--------|--------|------|
| | | | | |

### 전체 CPU Requests 변화

| 항목 | 변경 전 | 변경 후 |
|------|--------|--------|
| 전체 CPU Requests 합계 | 3960m | |
| 가용 CPU | 4000m | 4000m |
| 사용률 | 99% | |

---

## 4단계: 검증 결과

### k6 부하 테스트 재실행

> 테스트 조건: (시나리오, VU 수, 지속 시간 등)

#### 주요 메트릭 비교

| 메트릭 | 조정 전 | 조정 후 |
|--------|--------|--------|
| P99 응답시간 | | |
| 에러율 | | |
| Throughput (req/s) | | |
| CPU 사용률 (노드) | | |
| Memory 사용률 (노드) | | |

#### Grafana 스크린샷

> SLO Overview, JVM Analysis 대시보드 캡처

### trace_id 동작 검증

> 부하 테스트 중 Log-Trace Correlation 링크 동작 확인 여부

---

## 결론 및 교훈

> 분석 결과 요약 및 리소스 사이징 시 적용할 수 있는 일반 원칙 기록
