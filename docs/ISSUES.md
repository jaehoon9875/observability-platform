# 이슈 및 개선 항목

미해결 이슈와 개선 검토 항목을 관리하는 문서.
각 이슈는 관련 단계와 함께 기록하고, 해결 시 체크 후 해결 내용을 기재한다.

---

## 현재 우선순위

| 순위 | 이슈 | 상태 |
|------|------|------|
| 1 | CPU 리소스 분석 + throttling 대시보드 | 해결 |
| 2 | Error Budget 대시보드 추가 | 예정 |
| - | Grafana datasource 관리 구조 개선 | 해결 |
| - | ArgoCD — mysql-operator CRD OutOfSync | 해결 |
| - | ArgoCD — prometheus-stack Grafana admin 비밀번호 OutOfSync | 해결 |

---

## 미해결 이슈

없음.

---

## 개선 검토 항목

### [3단계] Error Budget 대시보드 추가

현재 SLO는 정의만 되어있고, Error Budget 소진 현황을 시각화하는 대시보드가 없다.
Error Budget 패널을 추가하면 "SLO를 설정했다"가 아니라 "SLO로 실제 운영 판단을 한다"는 수준으로 완성도가 올라간다.

- [ ] SLO Overview 대시보드(`dashboards/slo-overview.yaml`)에 Error Budget 패널 추가
  - 30일 Error Budget 소진율
    ```promql
    1 - (
      sum(rate(http_server_requests_seconds_count{status!~"5.."}[30d]))
      / sum(rate(http_server_requests_seconds_count[30d]))
    ) / (1 - 0.999)
    ```
  - 잔여 Error Budget(%) 및 소진 속도 시각화
- [ ] 대시보드 JSON 업데이트 후 ArgoCD sync로 반영

---

## 해결된 이슈

### [4단계] 부하 테스트 후속 작업 (2026-04-03 해결)

- **발견**: vus 40 부하 테스트 실행 중 order-service pod가 크래시/재시작 반복. HTTP 레벨 에러는 미발생(pod가 먼저 죽으므로)하여 기존 에러율 지표만으로는 장애를 감지할 수 없는 구조였음.
- **대응**:
  - SLO 대시보드에 Pod 재시작 횟수(`kube_pod_container_status_restarts_total`) 패널 추가 → HTTP 메트릭에 잡히지 않는 pod crash 장애 보완 (커밋 `40bc314`)
  - Pod 가용성(`avg_over_time(up[1h])`) 패널 추가 → scrape 기준 실제 가동률 시각화
  - SLO 에러율 패널 Y축 min/max 고정(0~1) → 10000% 튀는 현상 수정 (커밋 `f4be589`)
  - `payment_call_duration_seconds` histogram 활성화 → histogram_quantile 기반 패널 No Data 해결
- **결론**: 부하 상황에서 pod crash를 감지·가시화하는 대시보드 구축 완료. k6 → Prometheus 연동은 이 프로젝트 규모에서 불필요(홀딩 유지).

---

### Grafana datasource 관리 구조 개선 (해결)

Log-Trace Correlation 작업 중 발견된 구조적 문제들.

- **문제**: Grafana provisioning은 datasource가 이미 존재하면 UPDATE 방식으로 처리하며, 이 때 `secureJsonData`와 일부 `jsonData` 변경이 반영되지 않음. 또한 Grafana 12.x 버전 등에서 파일 기반 provisioning 적용 시 문제가 발생함.
- **해결**: `custom-values.yaml`의 provisioning 설정에 `deleteDatasources` 옵션을 추가하여 기존 datasource를 삭제 후 새로운 데이터소스를 재생성하도록 처리함.
  ```yaml
  deleteDatasources:
    - name: Tempo
      orgId: 1
    - name: Loki
      orgId: 1
  ```

---

### [9단계] 싱글노드 CPU 리소스 부족 (해결)

- **증상**: CPU requests 합계 3810m/4000m(95%), payment-service Pending, k6 Job 스케줄링 불가
- **근본 원인**: Loki chart 기본값 `chunksCache.allocatedMemory: 8192MB`가 그대로 적용되어 memcached 2개가 CPU 1000m(25%), 메모리 11GB(69%)를 점유
- **해결**: `infra/helm/loki/custom-values.yaml`에서 memcached 리소스 조정
  - `chunksCache`: allocatedCPU 500m→100m, allocatedMemory 8192→512MB
  - `resultsCache`: allocatedCPU 500m→100m, allocatedMemory 1024→256MB
- **결과**: CPU 3810m(95%)→3260m(81%), Memory 14693Mi(93%)→4811Mi(30%), payment-service 정상 스케줄링 확인
- **상세 분석**: `docs/resource-sizing-analysis.md` 참조

---

### [5단계] ArgoCD — mysql-operator CRD OutOfSync (2026-04-01 해결)

- **증상**: `innodbclusters.mysql.oracle.com` 등 `mysql-operator`가 설치하는 CRD의 다수 필드가 클러스터 측에만 존재하여 OutOfSync 발생.
- **근본 원인**: 쿠버네티스 API 서버가 CRD를 저장할 때 스키마를 정규화하면서 `description: ''` 같은 필드를 자동으로 추가함. Git과 클러스터 상태 간의 불일치가 발생하여 ArgoCD가 이를 OutOfSync로 감지.
- **시도 (실패)**: `ignoreDifferences`에 `jsonPointers`나 `jqPathExpressions`를 사용하여 `description` 필드를 무시하는 방법은 근본적인 해결책이 아니었음.
- **해결**: ArgoCD Application에 `argocd.argoproj.io/compare-options: ServerSideDiff=true` 어노테이션을 추가하여 문제를 해결함.
  - 이 옵션은 ArgoCD가 `kubectl diff` 대신 서버 측(Server-Side)에서 Diff를 계산하도록 하여, API 서버의 정규화로 인한 차이를 무시하고 CRD 불일치 문제를 근본적으로 해결함.
- **적용 파일**: `infra/argocd/obs-apps/mysql-operator.yaml`

---

### [2단계] Log-Trace Correlation — Loki → Tempo 링크 연동 (2026-03-31 해결)

- **증상**: Loki 로그에서 "Tempo에서 트레이스 보기" 링크 클릭 시 Tempo Explore 페이지로 이동하나 TraceQL 쿼리가 비어있고 No data
- **근본 원인**: Loki derived field에 `url` 필드 누락. `datasourceUid`만 설정하면 Grafana가 캡처한 trace_id 값을 Tempo에 전달할 경로를 알 수 없어 TraceQL 쿼리가 빈 상태로 열림
- **해결**: `url: "$${__value.raw}"` 추가 → 캡처된 trace_id 값을 그대로 Tempo에 전달
  - `custom-values.yaml` 수정 + Grafana API PUT으로 즉시 적용
  - 브라우저 새로고침 후 링크 정상 동작 확인
- **완료된 작업 전체**:
  - Alloy stage.json 필드명 오류 수정: `traceId` → `trace_id`, `spanId` → `span_id` (커밋 `d9ffb38`)
  - Grafana Loki Derived Fields: matcherType `label` → `regex`, matcherRegex `"trace_id":"([a-f0-9]{32})"` (커밋 `9818c51`)
  - Grafana DB에서 Loki datasource 삭제 후 API로 직접 생성 (UPDATE 미적용 문제 우회)
  - derived field `url: "$${__value.raw}"` 추가 (Grafana API PUT 적용)

---

### [9단계] Metric-Trace Correlation (Exemplar → Tempo) 구축 (2026-04-01 해결)

- **목표**: Alert 발생 → 메트릭 그래프 확인 → Exemplar 클릭 → Tempo trace 이동 흐름 완성
- **구현 내용**:
  - Prometheus `exemplar-storage` feature 활성화 (`prometheus.prometheusSpec.enableFeatures`)
  - 3개 서비스 pom.xml에 `micrometer-tracing-bridge-otel` 추가 (OTel Agent ↔ Micrometer 브릿지)
  - 3개 서비스 application.yml에 `http.server.requests` percentile histogram 활성화
  - Grafana Prometheus datasource Exemplar 연결: `sidecar.datasources.exemplarTraceIdDestinations.traceIdLabelName: trace_id`
- **트러블슈팅**:
  - `defaultDatasourceEnabled: false` + `additionalDataSources` 교체 방식 → chart 내부 프로비저닝 충돌로 데이터 전체 소실. 기본 datasource 유지 방식으로 롤백
  - chart 기본 exemplar label name이 `traceID`로 설정됨 → OTel Agent 실제 필드명 `trace_id`와 불일치로 Tempo 링크 미생성. `traceIdLabelName: trace_id`로 수정하여 해결
- **완료 기준**: Prometheus Explore에서 Exemplar 점 클릭 시 Tempo trace로 직접 이동 확인 ✅

---

### [5단계] ArgoCD — prometheus-stack Grafana admin 비밀번호 OutOfSync (2026-04-01 해결)

- **증상**: Grafana Secret(`my-kube-prometheus-stack-grafana`) 및 Deployment OutOfSync — Deployment 내 `checksum/secret` 어노테이션이 클러스터와 Git 값이 불일치
- **실제 원인**: Grafana UI/API를 통해 admin 비밀번호를 직접 변경했기 때문에, 클러스터의 Secret과 Deployment checksum이 Helm chart가 렌더링하는 값과 달라진 것
- **해결**: Sealed Secrets 도입으로 비밀번호를 Git에서 관리
  - `grafana.admin.existingSecret: grafana-admin-secret` 설정 → Helm이 Secret을 직접 생성하지 않으므로 checksum drift 발생 안 함
  - `infra/manifests/monitoring-secrets/grafana-admin-sealedsecret.yaml`에서 SealedSecret으로 관리
  - ArgoCD Application 2개 등록(`sealed-secrets`, `monitoring-secrets`) 후 prometheus-stack sync로 OutOfSync 해소 확인

---

### [5단계] prometheus-stack Degraded 상태 (2026-03-30 해결)

**원인 1 — ArgoCD PreSync Hook Job hang**
`admission-create` Job이 클러스터에서 이미 완료/삭제됐으나 ArgoCD가 `hookPhase: Running`으로 오인하여 sync operation이 28시간 이상 정체.
`kubectl patch application prometheus-stack -n argocd --type merge -p '{"operation": null}'` 로 operation 강제 종료 후 재sync로 해소.

**원인 2 — Grafana init-chown-data Permission denied (SSA 필드 소유권 충돌)**
초기 설치 시 `helm upgrade` CLI로 직접 배포했기 때문에 `helm` 필드 매니저가 Grafana Deployment의 `initContainers` 필드를 소유.
이후 `initChownData.enabled: false`(커밋 `4fde0c1`)를 custom-values.yaml에 적용했으나, ArgoCD SSA는 자신(`argocd-controller`)이 소유하지 않은 필드를 제거하지 않아 init 컨테이너가 계속 실행됨.
`kubectl patch deployment ... --type=json -p='[{"op":"remove","path":"/spec/template/spec/initContainers"}]'` 로 강제 제거 후 일시 정상화.
→ **근본 원인**: ArgoCD로 관리되는 리소스를 `helm upgrade` CLI로 직접 수정하면 SSA 필드 소유권이 분리되어 ArgoCD가 특정 필드를 관리하지 못하는 문제 발생.

**추가 수정 — fsGroup: 472 + initChownData: false 조합이 올바른 해결책**
`fsGroup: 472`를 설정하면 kubelet이 PV 마운트 시 자동으로 GID 472로 소유권을 변경하므로 init container 없이도 최초 기동부터 권한 문제가 없다.
`initChownData: true`로 init container를 활성화하면 `CAP_CHOWN`만 보유한 채로 mode 700 디렉토리(`pdf/csv/png`)를 재귀 탐색 시도 → `DAC_OVERRIDE` 부재로 Permission denied 발생.
→ `securityContext.fsGroup: 472` 유지, `initChownData.enabled: false`로 최종 수정.
