# dashboards/ 디렉토리 가이드

## 개요

이 디렉토리는 Grafana 대시보드를 `ConfigMap` K8s 리소스로 관리한다.
kube-prometheus-stack의 Grafana 사이드카가 `grafana_dashboard: "1"` 라벨이 붙은
ConfigMap을 자동으로 감지하여 대시보드를 로드한다.

## 파일 구성

| 파일 | 대시보드 이름 | 설명 |
| --- | --- | --- |
| `slo-overview.yaml` | SLO Overview | 가용성, 에러율, P99 레이턴시, 주문 처리 현황 |
| `jvm-analysis.yaml` | JVM Analysis | Heap/Non-Heap 메모리, GC, 스레드 상태 |

## 클러스터 환경 정보

- **Grafana 사이드카 감지 라벨**: `grafana_dashboard: "1"`
  - ConfigMap의 `metadata.labels`에 반드시 이 라벨이 있어야 자동 로드된다.
- **배포 네임스페이스**: `monitoring`

## 적용 및 확인 방법

```bash
# 대시보드 적용
kubectl apply -f dashboards/slo-overview.yaml
kubectl apply -f dashboards/jvm-analysis.yaml

# ConfigMap 생성 확인
kubectl get configmap -n monitoring | grep dashboard

# Grafana에서 확인
# Grafana UI → Dashboards → Browse → "SLO Overview" / "JVM Analysis" 검색
```

## ConfigMap 기본 구조

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: {dashboard-name}-dashboard
  namespace: monitoring
  labels:
    grafana_dashboard: "1"   # 필수 — 이 라벨이 없으면 Grafana가 감지하지 못함
data:
  {filename}.json: |          # 키 이름이 Grafana 내부 파일명이 됨
    {
      "uid": "고유 ID",        # 같은 uid가 이미 있으면 덮어씀
      "title": "대시보드 제목",
      ...
    }
```

## 대시보드 수정 방법

두 가지 방법 중 선택:

### 방법 1: YAML 직접 수정 후 재적용 (권장)
```bash
# YAML 수정 후
kubectl apply -f dashboards/slo-overview.yaml
# Grafana가 수십 초 내로 자동 갱신
```

### 방법 2: Grafana UI에서 수정 후 JSON Export → YAML 반영
1. Grafana UI에서 대시보드 편집
2. Dashboard settings → JSON Model 복사
3. 해당 YAML 파일의 JSON 부분을 교체
4. `kubectl apply`로 반영

## 주의사항

- `uid` 값이 중복되면 기존 대시보드를 덮어쓴다. 새 대시보드 추가 시 고유한 uid를 사용한다.
- Grafana UI에서 직접 수정한 내용은 Pod 재시작 시 초기화된다. 반드시 YAML에도 반영해야 한다.
- `job` 라벨 값은 ServiceMonitor 설정에 따라 달라질 수 있으므로,
  Prometheus UI에서 실제 job 값을 확인 후 PromQL을 수정한다.
