# 이슈 및 개선 항목

미해결 이슈와 개선 검토 항목을 관리하는 문서.
각 이슈는 관련 단계와 함께 기록하고, 해결 시 체크 후 해결 내용을 기재한다.

---

## 미해결 이슈

### [5단계] ArgoCD — mysql-operator CRD OutOfSync

- **증상**: `innodbclusters.mysql.oracle.com` CRD 하나만 지속적으로 OutOfSync. `kubectl diff --server-side --field-manager=argocd-controller`로 확인 시 실제 내용 diff는 없음
- **조치 완료**: `infra/argocd/obs-apps/mysql-operator.yaml`에 `ServerSideApply=true` 추가 후 `kubectl apply`로 클러스터에 반영
- **미해결**: ArgoCD selfHeal 자동 sync operation에 `ServerSideApply=true`가 포함되지 않는 원인 불명. 내용 diff 없이 ArgoCD가 OutOfSync로 판단하는 이유 미파악
- **다음 접근 방향**:
  - ArgoCD UI에서 실제 diff 내용 직접 확인
  - `ignoreDifferences` 규칙을 Application에 추가하여 CRD drift 무시 검토
  - ArgoCD v3.3.6의 serverSideDiff + selfHeal 동작 관련 이슈 트래커 확인

### [5단계] ArgoCD — prometheus-stack Grafana SSA 충돌

- **증상**: Grafana Secret/Deployment OutOfSync — `helm` manager가 소유한 필드를 `argocd-controller`가 덮어쓰지 못하는 상태
- **근본 원인**: 초기 설치 시 `helm upgrade` CLI로 직접 배포했기 때문에 `helm` 필드 매니저가 일부 필드를 소유. ArgoCD SSA는 자신이 소유하지 않은 필드를 제거하지 못함
- **다음 접근 방향**:
  - `ignoreDifferences` 규칙 추가 또는 SSA force-conflicts 적용 검토

### [5단계] ArgoCD — PreSync Hook Job hang 반복

- **증상**: `my-kube-prometheus-stack-admission-create` Job이 Complete 상태인데 ArgoCD가 Running으로 오인하여 sync가 무한 대기
- **임시 조치**: `kubectl patch application prometheus-stack -n argocd --type merge -p '{"operation": null}'` 로 수동 해소
- **다음 접근 방향**:
  - prometheus-stack ArgoCD Application에 hook 정책 조정 또는 `ignoreDifferences` 적용으로 근본 해결 검토

### [2단계] Log-Trace Correlation — Tempo 링크 클릭 시 No data

- **증상**: Loki 로그에서 "Tempo에서 트레이스 보기" 링크 클릭 시 Tempo Explore 페이지로 이동하나 TraceQL 쿼리가 비어있고 No data
- **현황**: Tempo에는 trace 데이터가 정상 저장되어 있음 (API 직접 조회로 확인)
- **추정 원인**: Grafana Tempo 플러그인이 derived field 값을 TraceQL 쿼리로 변환하지 못하는 것으로 추정 (datasourceUid 방식의 internal link에서 trace_id 값 전달 경로 미확인)
- **선행 완료 작업**:
  - Alloy stage.json 필드명 오류 수정: `traceId` → `trace_id`, `spanId` → `span_id`
  - Grafana Loki Derived Fields 설정: matcherType `label` → `regex`, matcherRegex `"trace_id":"([a-f0-9]{32})"`
  - Grafana DB에서 Loki datasource 삭제 후 API로 직접 생성 (UPDATE 미적용 문제 우회)

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

Grafana 12.x부터 datasource 관리가 Kubernetes-native 스토리지 방식으로 전환되면서, 기존 파일 기반 provisioning(`/etc/grafana/provisioning/datasources/`)이 기동 시 자동 실행되지 않는다. 현재 Loki datasource는 pod 재시작 시 사라지는 상태.

- [ ] Grafana 12.x의 새로운 datasource provisioning 방식 파악 후 적용
  - `datasources.grafana.app` API group 방식 검토
  - 또는 초기화 Job/init container에서 `POST /api/datasources` 호출하는 방식 검토

---

## 해결된 이슈

### [5단계] prometheus-stack Degraded 상태 (2026-03-30 해결)

**원인 1 — ArgoCD PreSync Hook Job hang**
`admission-create` Job이 클러스터에서 이미 완료/삭제됐으나 ArgoCD가 `hookPhase: Running`으로 오인하여 sync operation이 28시간 이상 정체.
`kubectl patch application prometheus-stack -n argocd --type merge -p '{"operation": null}'` 로 operation 강제 종료 후 재sync로 해소.

**원인 2 — Grafana init-chown-data Permission denied (SSA 필드 소유권 충돌)**
초기 설치 시 `helm upgrade` CLI로 직접 배포했기 때문에 `helm` 필드 매니저가 Grafana Deployment의 `initContainers` 필드를 소유.
이후 `initChownData.enabled: false`(커밋 `4fde0c1`)를 custom-values.yaml에 적용했으나, ArgoCD SSA는 자신(`argocd-controller`)이 소유하지 않은 필드를 제거하지 않아 init 컨테이너가 계속 실행됨.
`kubectl patch deployment ... --type=json -p='[{"op":"remove","path":"/spec/template/spec/initContainers"}]'` 로 강제 제거 후 정상화.
→ **근본 원인**: ArgoCD로 관리되는 리소스를 `helm upgrade` CLI로 직접 수정하면 SSA 필드 소유권이 분리되어 ArgoCD가 특정 필드를 관리하지 못하는 문제 발생.

### [2단계] Log-Trace Correlation 파이프라인 설정 (완료)

- Alloy stage.json 필드명 오류 수정 완료: `traceId` → `trace_id`, `spanId` → `span_id` (커밋 `d9ffb38`)
- Grafana Loki 데이터소스 Derived Fields 설정 완료 (커밋 `9818c51`)
  - matcherType: `label` → `regex` 변경. `label` 방식은 Loki stream label(indexed label)을 지원하지 않아 링크 미생성
  - matcherRegex: `"trace_id":"([a-f0-9]{32})"` — 로그 라인 JSON 본문에서 직접 캡처
- Grafana provisioning UPDATE 문제 → Grafana API로 직접 datasource 생성으로 우회
