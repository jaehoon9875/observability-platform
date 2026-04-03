#!/usr/bin/env bash
# k6 부하 테스트 실행/중단 스크립트
#
# 사용법:
#   ./scripts/run-k6.sh <type> [옵션]
#
# type:
#   order-flow          정상 트래픽 시나리오
#   spike-test          급증 시나리오
#   stop [시나리오]     실행 중인 Job 중단 (생략 시 전체 중단)
#
# 옵션 (order-flow):
#   --vus <n>          최대 VU 수 (기본값: 10)
#   --sleep <s>        요청 간 대기 시간 초 (기본값: 1.0)
#   --rampup <dur>     ramp-up 시간 (기본값: 1m)
#   --sustain <dur>    유지 시간 (기본값: 3m)
#   --rampdown <dur>   ramp-down 시간 (기본값: 1m)
#
# 옵션 (spike-test):
#   --base-vus <n>     기준 VU 수 (기본값: 5)
#   --vus <n>          급증 VU 수 (기본값: 20)
#   --sleep <s>        요청 간 대기 시간 초 (기본값: 0.3)
#
# 예시:
#   ./scripts/run-k6.sh order-flow
#   ./scripts/run-k6.sh order-flow --vus 20 --sleep 0.5
#   ./scripts/run-k6.sh spike-test --vus 40
#   ./scripts/run-k6.sh spike-test --vus 40 --sleep 0.1
#   ./scripts/run-k6.sh stop
#   ./scripts/run-k6.sh stop spike-test

set -euo pipefail

NAMESPACE="obs-apps"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

# ── 인자 파싱 ──────────────────────────────────────────────

TYPE="${1:-}"

if [[ -z "${TYPE}" ]]; then
  echo "사용법: $0 <order-flow|spike-test|stop> [옵션]"
  echo "  $0 order-flow --vus 20"
  echo "  $0 spike-test --vus 40 --sleep 0.1"
  echo "  $0 stop"
  echo "  $0 stop spike-test"
  exit 1
fi
shift

# ── stop 처리 ──────────────────────────────────────────────

if [[ "${TYPE}" == "stop" ]]; then
  TARGET="${1:-all}"
  case "${TARGET}" in
    order-flow) STOP_JOBS=("k6-order-flow") ;;
    spike-test) STOP_JOBS=("k6-spike-test") ;;
    all)        STOP_JOBS=("k6-order-flow" "k6-spike-test") ;;
    *) echo "오류: 알 수 없는 시나리오 '${TARGET}'. order-flow, spike-test, 또는 생략하세요."; exit 1 ;;
  esac

  DELETED=0
  for JOB in "${STOP_JOBS[@]}"; do
    if kubectl get job "${JOB}" -n "${NAMESPACE}" &>/dev/null; then
      echo ">>> Job '${JOB}' 삭제 중..."
      kubectl delete job "${JOB}" -n "${NAMESPACE}"
      DELETED=$((DELETED + 1))
    else
      echo ">>> Job '${JOB}' 는 실행 중이 아닙니다."
    fi
  done

  [[ ${DELETED} -gt 0 ]] && echo ">>> 완료: ${DELETED}개 Job 삭제됨" || true
  exit 0
fi

# 기본값
MAX_VUS="10"
BASE_VUS="5"
SPIKE_VUS="20"
SLEEP_SEC=""
RAMPUP_DUR="1m"
SUSTAIN_DUR="3m"
RAMPDOWN_DUR="1m"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --vus)       MAX_VUS="$2"; SPIKE_VUS="$2"; shift 2 ;;
    --base-vus)  BASE_VUS="$2";                shift 2 ;;
    --sleep)     SLEEP_SEC="$2";               shift 2 ;;
    --rampup)    RAMPUP_DUR="$2";              shift 2 ;;
    --sustain)   SUSTAIN_DUR="$2";             shift 2 ;;
    --rampdown)  RAMPDOWN_DUR="$2";            shift 2 ;;
    *) echo "오류: 알 수 없는 옵션 '$1'"; exit 1 ;;
  esac
done

# ── 시나리오별 설정 ────────────────────────────────────────

case "${TYPE}" in
  order-flow)
    JOB_NAME="k6-order-flow"
    SCRIPT_FILE="order-flow.js"
    [[ -z "${SLEEP_SEC}" ]] && SLEEP_SEC="1.0"
    ;;
  spike-test)
    JOB_NAME="k6-spike-test"
    SCRIPT_FILE="spike-test.js"
    [[ -z "${SLEEP_SEC}" ]] && SLEEP_SEC="0.3"
    ;;
  *)
    echo "오류: 알 수 없는 type '${TYPE}'. order-flow 또는 spike-test 를 사용하세요."
    exit 1
    ;;
esac

# ── 실행 설정 출력 ─────────────────────────────────────────

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo " k6 부하 테스트 시작"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo " 시나리오  : ${TYPE}"
if [[ "${TYPE}" == "order-flow" ]]; then
  echo " 최대 VU   : ${MAX_VUS}"
  echo " 구성      : ramp-up ${RAMPUP_DUR} / 유지 ${SUSTAIN_DUR} / ramp-down ${RAMPDOWN_DUR}"
else
  echo " 기준 VU   : ${BASE_VUS}"
  echo " 급증 VU   : ${SPIKE_VUS}"
fi
echo " sleep     : ${SLEEP_SEC}s"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# ── 이전 Job 정리 ──────────────────────────────────────────

if kubectl get job "${JOB_NAME}" -n "${NAMESPACE}" &>/dev/null; then
  echo ">>> 이전 Job '${JOB_NAME}' 삭제 중..."
  kubectl delete job "${JOB_NAME}" -n "${NAMESPACE}"
  sleep 2
fi

# ── ConfigMap 적용 ─────────────────────────────────────────

echo ">>> ConfigMap 적용 중..."
kubectl apply -f "${REPO_ROOT}/infra/manifests/k6/configmap.yaml" -n "${NAMESPACE}"

# ── Job 동적 생성 ──────────────────────────────────────────

echo ">>> Job '${JOB_NAME}' 생성 중..."

kubectl apply -f - <<EOF
apiVersion: batch/v1
kind: Job
metadata:
  name: ${JOB_NAME}
  namespace: ${NAMESPACE}
  labels:
    app.kubernetes.io/name: k6
    app.kubernetes.io/part-of: observability-platform
spec:
  ttlSecondsAfterFinished: 300
  backoffLimit: 0
  template:
    metadata:
      labels:
        app: k6
    spec:
      restartPolicy: Never
      containers:
        - name: k6
          image: grafana/k6:latest
          args:
            - run
            - /scripts/${SCRIPT_FILE}
          env:
            - name: BASE_URL
              value: "http://order-service:8080"
            - name: MAX_VUS
              value: "${MAX_VUS}"
            - name: BASE_VUS
              value: "${BASE_VUS}"
            - name: SPIKE_VUS
              value: "${SPIKE_VUS}"
            - name: SLEEP
              value: "${SLEEP_SEC}"
            - name: RAMPUP_DUR
              value: "${RAMPUP_DUR}"
            - name: SUSTAIN_DUR
              value: "${SUSTAIN_DUR}"
            - name: RAMPDOWN_DUR
              value: "${RAMPDOWN_DUR}"
          volumeMounts:
            - name: scripts
              mountPath: /scripts
          resources:
            requests:
              cpu: "100m"
              memory: "128Mi"
            limits:
              cpu: "500m"
              memory: "256Mi"
      volumes:
        - name: scripts
          configMap:
            name: k6-scripts
EOF

# ── Pod 대기 및 로그 스트리밍 ──────────────────────────────

echo ">>> Pod 시작 대기 중..."
kubectl wait --for=condition=ready pod \
  -l job-name="${JOB_NAME}" \
  -n "${NAMESPACE}" \
  --timeout=60s

echo ">>> 로그 스트리밍 시작 (Ctrl+C로 중단 가능, Job은 계속 실행됨)"
echo "─────────────────────────────────────────────────────────"
kubectl logs -f job/"${JOB_NAME}" -n "${NAMESPACE}"
