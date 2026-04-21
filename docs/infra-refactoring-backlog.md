# infra 리팩토링 백로그

`infra/` 하위 아키텍처/설정 개선 항목을 추적하는 문서.
9단계(전체 리뷰 및 개선)에서 우선순위 기준으로 순차 적용한다.

## 운영 원칙

- 브랜치 전략: 기능 1개당 1브랜치 (`refactor/...`) + 1PR
- 반영 방식: `infra/` 파일 수정 → Git push → ArgoCD sync
- 금지: ArgoCD 관리 리소스를 `kubectl apply`, `helm upgrade`로 직접 수정

## 진행 상태

상태 값: `TODO`, `DOING`, `DONE`, `HOLD`

## 우선순위 상 (즉시)

1. `DONE` OTel 공통 env 중복 제거 (3개 서비스 Deployment)
   - 대상: `infra/manifests/sample-apps/*/deployment.yaml`
   - 액션: 공통 ConfigMap 도입, `OTEL_SERVICE_NAME`만 서비스별 유지

2. `DONE` Probe 경로 분리 (liveness/readiness)
   - 대상: `infra/manifests/sample-apps/*/deployment.yaml`
   - 액션: `/actuator/health/liveness`, `/actuator/health/readiness`로 분리

3. `DONE` k6 이미지 버전 고정 (`latest` 제거)
   - 대상: `infra/manifests/k6/job.yaml`
   - 액션: `grafana/k6:1.7.1` 명시

4. `DONE` 앱 DB Secret GitOps 편입 (SealedSecret)
   - 대상: `infra/manifests/sample-apps/*/`
   - 액션: 서비스별 SealedSecret 추가 후 수동 시크릿 의존 제거

## 우선순위 중

5. `DONE` ArgoCD sync-wave로 의존 순서 강제
   - 대상: `infra/argocd/obs-apps/*.yaml`
   - 액션: operator(-1) → CR(0) → app(1) wave 지정

6. `TODO` ServiceMonitor selector 표준 라벨로 통일
   - 대상: `infra/manifests/**/service-monitor.yaml`
   - 액션: `app.kubernetes.io/name` 기반 selector로 정리

7. `TODO` k6 시나리오 환경변수 분리
   - 대상: `infra/manifests/k6/*`
   - 액션: 시나리오별 ConfigMap 분리 및 실행 스크립트 정리

8. `TODO` 앱 startupProbe 도입 (의존성 초기 지연 완충)
   - 대상: `infra/manifests/sample-apps/*/deployment.yaml`
   - 액션: 서비스별 startupProbe 추가로 초기 부팅 지연 시 불필요한 재시작 방지

9. `TODO` 앱 readiness에 의존성(선택적) 반영 기준 정리
   - 대상: `sample-apps/*/src/main/resources/application.yml`, `infra/manifests/sample-apps/*/deployment.yaml`
   - 액션: DB/Kafka readiness 반영 범위(필수/선택)와 운영 기준 문서화

10. `TODO` DB/Kafka 연결 재시도·타임아웃 기본값 표준화
    - 대상: `sample-apps/*/src/main/resources/application.yml`
    - 액션: 커넥션/클라이언트 타임아웃 및 재시도(backoff) 기본값 합의 후 공통 반영

## 우선순위 하

11. `TODO` Alloy Loki 엔드포인트 관리 개선
   - 대상: `infra/helm/alloy/custom-values.yaml`
   - 액션: 하드코딩 축소 및 변경 포인트 주석 강화

12. `TODO` Tempo custom-values 의도 명시
   - 대상: `infra/helm/tempo/custom-values.yaml`
   - 액션: 현재 의존 기본값(포트/스토리지) 문서화

13. `TODO` ArgoCD AppProject 분리 검토
    - 대상: `infra/argocd/**`
    - 액션: `monitoring-stack`, `obs-apps` 프로젝트 분리 여부 결정

## 제안 작업 순서

1. F1 OTel env 중복 제거
2. F2 Probe 분리
3. F3 k6 버전 고정
4. F5 sync-wave 적용
5. F8/F9/F10(앱 복원력) 우선 적용
6. F4/F6/F7/F11/F12/F13 순차 적용
