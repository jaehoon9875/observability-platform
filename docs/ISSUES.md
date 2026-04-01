# 이슈 및 개선 항목

미해결 이슈와 개선 검토 항목을 관리하는 문서.
각 이슈는 관련 단계와 함께 기록하고, 해결 시 체크 후 해결 내용을 기재한다.

---

## 현재 우선순위

| 순위 | 이슈 | 상태 |
|------|------|------|
| 1 | CPU 리소스 분석 + throttling 대시보드 | 예정 |
| 2 | Error Budget 대시보드 추가 | 예정 |
| - | Grafana datasource 관리 구조 개선 | 홀딩 |
| - | ArgoCD — mysql-operator CRD OutOfSync | 해결 |
| - | ArgoCD — prometheus-stack Grafana admin 비밀번호 OutOfSync | 해결 |

---

## 미해결 이슈

### [9단계] 싱글노드 CPU 리소스 부족

- **증상**: CPU requests 합계가 3960m/4000m(99%)로 k6 Job 등 추가 Pod 스케줄링 불가
- **주요 원인**: loki-chunks-cache, loki-results-cache(memcached) 각 500m 요청 — 싱글노드 환경에 과도
- **분석 계획**: `docs/resource-sizing-analysis.md` 참조 (실제 사용량 분석 → 근거 기반 조정 → 검증 순서)
- **남은 작업**:
  - [ ] 실제 CPU/Memory 사용량 수집 및 requests/limits 대비 분석 (`docs/resource-sizing-analysis.md` 1단계)
  - [ ] 서비스별 심층 분석 — Java JVM, Loki memcached, Kafka (`docs/resource-sizing-analysis.md` 2단계)
  - [ ] 분석 결과 기반으로 infra/ 값 조정 (`docs/resource-sizing-analysis.md` 3단계)
  - [ ] 조정 후 k6 부하 테스트 재실행 및 trace_id 동작 검증 (`docs/resource-sizing-analysis.md` 4단계)

### [4단계] 부하 테스트 후속 작업

- [ ] 부하 테스트 병목 분석 결과 문서화
- [ ] k6 → Prometheus 연동 (필요 시)

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

### Grafana datasource 관리 구조 개선

Log-Trace Correlation 작업 중 발견된 구조적 문제들.

**문제 1 — provisioning UPDATE로 인한 jsonData/secureJsonData 미적용**

Grafana provisioning은 datasource가 이미 존재하면 UPDATE 방식으로 처리하며, 이 때 `secureJsonData`와 일부 `jsonData` 변경이 반영되지 않는다.

- [ ] provisioning 파일에 `deleteDatasources` 옵션 추가
  ```yaml
  apiVersion: 1
  deleteDatasources:
    - name: Loki
      orgId: 1
  datasources:
    - name: Loki
      ...
  ```
  이렇게 하면 Grafana가 기존 datasource를 삭제 후 재생성하므로 UPDATE 문제가 해소된다.

**문제 2 — Grafana 12.4.1에서 기동 시 `provisioning.datasources` 미실행**

Grafana 12.x에서 파일 기반 datasource provisioning이 재시작 시 실행되지 않는다. 이로 인해 Tempo datasource가 초기 provisioning 상태(readOnly=true)로 고정되어 API/UI/ConfigMap 변경이 모두 불가. Trace→Log(`filterByTraceID`) 적용이 이 단계에서 중단된 상태.

- [ ] Grafana 12.x의 새로운 datasource provisioning 방식 파악 후 적용
  - `datasources.grafana.app` API group 방식 또는 init container에서 API 호출 방식 검토
  - 해결 후 Tempo datasource `tracesToLogsV2.filterByTraceID: true` 적용

---

## 해결된 이슈

### [5단계] ArgoCD — mysql-operator CRD OutOfSync (2026-04-01 해결)

- **증상**: `innodbclusters.mysql.oracle.com` 등 `mysql-operator`가 설치하는 CRD의 다수 필드가 클러스터 측에만 존재하여 OutOfSync 발생.
- **근본 원인**: 쿠버네티스 API 서버가 CRD를 저장할 때 스키마를 정규화하면서 `description: ''` 같은 필드를 자동으로 추가함. Git과 클러스터 상태 간의 불일치가 발생하여 ArgoCD가 이를 OutOfSync로 감지.
- **시도 (실패)**: `ignoreDifferences`에 `jsonPointers`나 `jqPathExpressions`를 사용하여 `description` 필드를 무시하는 방법은 근본적인 해결책이 아니었음.
- **해결**: ArgoCD Application에 `argocd.argoproj.io/compare-options: ServerSideDiff=true` 어노테이션을 추가하여 문제를 해결함.
  - 이 옵션은 ArgoCD가 `kubectl diff` 대신 서버 측(Server-Side)에서 Diff를 계산하도록 하여, API 서버의 정규화로 인한 차이를 무시하고 CRD 불일치 문제를 근본적으로 해결함.
- **적용 파일**: `infra/argocd/obs-apps/mysql-operator.yaml`

### [2단계] Log-Trace Correlation — Loki → Tempo 링크 연동 (2026-03-31 해결)

- **증상**: Loki 로그에서 "Tempo에서 트레이스 보기" 링크 클릭 시 Tempo Explore 페이지로 이동하나 TraceQL 쿼리가 비어있고 No data
- **근본 원인**: Loki derived field에 `url` 필드 누락. `datasourceUid`만 설정하면 Grafana가 캡처한 trace_id 값을 Tempo에 전달할 경로를 알 수 없어 TraceQL 쿼리가 빈 상태로 열림
- **해결**: `url: "${__value.raw}"` 추가 → 캡처된 trace_id 값을 그대로 Tempo에 전달
  - `custom-values.yaml` 수정 + Grafana API PUT으로 즉시 적용
  - 브라우저 새로고침 후 링크 정상 동작 확인
- **완료된 작업 전체**:
  - Alloy stage.json 필드명 오류 수정: `traceId` → `trace_id`, `spanId` → `span_id` (커밋 `d9ffb38`)
  - Grafana Loki Derived Fields: matcherType `label` → `regex`, matcherRegex `"trace_id":"([a-f0-9]{32})"` (커밋 `9818c51`)
  - Grafana DB에서 Loki datasource 삭제 후 API로 직접 생성 (UPDATE 미적용 문제 우회)
  - derived field `url: "${__value.raw}"` 추가 (Grafana API PUT 적용)

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



### [5단계] ArgoCD — prometheus-stack Grafana admin 비밀번호 OutOfSync (2026-04-01 해결)

- **증상**: Grafana Secret(`my-kube-prometheus-stack-grafana`) 및 Deployment OutOfSync — Deployment 내 `checksum/secret` 어노테이션이 클러스터와 Git 값이 불일치
- **실제 원인**: Grafana UI/API를 통해 admin 비밀번호를 직접 변경했기 때문에, 클러스터의 Secret과 Deployment checksum이 Helm chart가 렌더링하는 값과 달라진 것
- **해결**: Sealed Secrets 도입으로 비밀번호를 Git에서 관리
  - `grafana.admin.existingSecret: grafana-admin-secret` 설정 → Helm이 Secret을 직접 생성하지 않으므로 checksum drift 발생 안 함
  - `infra/manifests/monitoring-secrets/grafana-admin-sealedsecret.yaml`에서 SealedSecret으로 관리
  - ArgoCD Application 2개 등록(`sealed-secrets`, `monitoring-secrets`) 후 prometheus-stack sync로 OutOfSync 해소 확인

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

