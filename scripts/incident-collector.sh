#!/usr/bin/env bash
# 장애 시 Pod 진단 정보 자동 수집 스크립트
#
# 사용법:
#   ./scripts/incident-collector.sh [옵션]
#
# 옵션:
#   -n, --namespace <ns>   대상 네임스페이스 (기본값: obs-apps)
#   -a, --all-namespaces   모든 네임스페이스 대상
#   -o, --output <dir>     결과 저장 상위 디렉토리 (기본값: ./incident-reports)
#   --lines <n>            수집할 로그 라인 수 (기본값: 200)
#   -h, --help             도움말 출력
#
# 수집 항목:
#   - Pod 목록 및 상태
#   - kubectl describe pod (각 Pod)
#   - kubectl logs (각 Pod, 이전 컨테이너 포함)
#   - kubectl get events (네임스페이스별)
#   - kubectl top pod (리소스 사용량)
#
# 예시:
#   ./scripts/incident-collector.sh
#   ./scripts/incident-collector.sh -n monitoring
#   ./scripts/incident-collector.sh --all-namespaces
#   ./scripts/incident-collector.sh -n obs-apps --lines 500 -o /tmp/incidents

set -euo pipefail

# ── 기본값 ──────────────────────────────────────────────────

NAMESPACE="obs-apps"
ALL_NAMESPACES=false
OUTPUT_BASE_DIR="./incident-reports"
LOG_LINES=200
TIMESTAMP="$(date '+%Y%m%d-%H%M%S')"

# ── 인자 파싱 ──────────────────────────────────────────────

usage() {
  sed -n '/^# 사용법/,/^$/p' "$0" | grep '^#' | sed 's/^# \{0,1\}//'
  exit 0
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    -n|--namespace)     NAMESPACE="$2";          shift 2 ;;
    -a|--all-namespaces) ALL_NAMESPACES=true;    shift   ;;
    -o|--output)        OUTPUT_BASE_DIR="$2";    shift 2 ;;
    --lines)            LOG_LINES="$2";           shift 2 ;;
    -h|--help)          usage ;;
    *) echo "오류: 알 수 없는 옵션 '$1'"; exit 1 ;;
  esac
done

# ── 네임스페이스 목록 결정 ────────────────────────────────

if "${ALL_NAMESPACES}"; then
  NAMESPACES="$(kubectl get namespaces -o jsonpath='{.items[*].metadata.name}')"
else
  NAMESPACES="${NAMESPACE}"
fi

# ── 출력 디렉토리 준비 ────────────────────────────────────

OUTPUT_DIR="${OUTPUT_BASE_DIR}/incident-${TIMESTAMP}"
mkdir -p "${OUTPUT_DIR}"

# ── 유틸 함수 ────────────────────────────────────────────

log() { echo "[$(date '+%H:%M:%S')] $*"; }

section() {
  echo ""
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  echo "  $*"
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
}

run_cmd() {
  # 명령 실행 후 결과를 파일로 저장. 실패해도 중단하지 않음.
  local out_file="$1"; shift
  mkdir -p "$(dirname "${out_file}")"
  if "$@" > "${out_file}" 2>&1; then
    :
  else
    echo "(명령 실패 또는 결과 없음: $*)" >> "${out_file}"
  fi
}

# ── 수집 시작 ────────────────────────────────────────────

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo " Incident Collector"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo " 수집 시각  : ${TIMESTAMP}"
if "${ALL_NAMESPACES}"; then
  echo " 대상       : 전체 네임스페이스"
else
  echo " 대상       : ${NAMESPACE}"
fi
echo " 로그 라인  : 최근 ${LOG_LINES}줄"
echo " 저장 위치  : ${OUTPUT_DIR}"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# summary.txt 헤더
SUMMARY_FILE="${OUTPUT_DIR}/summary.txt"
{
  echo "=== Incident Report ==="
  echo "수집 시각 : ${TIMESTAMP}"
  echo "kubectl 컨텍스트 : $(kubectl config current-context 2>/dev/null || echo 'unknown')"
  echo ""
} > "${SUMMARY_FILE}"

# ── 네임스페이스별 수집 ──────────────────────────────────

for NS in ${NAMESPACES}; do
  section "네임스페이스: ${NS}"
  NS_DIR="${OUTPUT_DIR}/${NS}"
  mkdir -p "${NS_DIR}"

  # 1. Pod 목록
  log "[${NS}] Pod 목록 수집 중..."
  run_cmd "${NS_DIR}/pods-list.txt" \
    kubectl get pods -n "${NS}" -o wide

  # summary에 Pod 상태 추가
  {
    echo "--- ${NS} ---"
    kubectl get pods -n "${NS}" -o wide 2>/dev/null || echo "(조회 실패)"
    echo ""
  } >> "${SUMMARY_FILE}"

  # 2. Events
  log "[${NS}] Events 수집 중..."
  run_cmd "${NS_DIR}/events.txt" \
    kubectl get events -n "${NS}" --sort-by='.lastTimestamp'

  # 3. Top pods (metrics-server 필요)
  log "[${NS}] 리소스 사용량(top) 수집 중..."
  run_cmd "${NS_DIR}/top-pods.txt" \
    kubectl top pods -n "${NS}"

  # 4. Pod별 상세 수집
  PODS="$(kubectl get pods -n "${NS}" -o jsonpath='{.items[*].metadata.name}' 2>/dev/null || true)"

  if [[ -z "${PODS}" ]]; then
    log "[${NS}] Pod 없음. 건너뜀."
    continue
  fi

  for POD in ${PODS}; do
    log "[${NS}/${POD}] describe / logs 수집 중..."
    POD_DIR="${NS_DIR}/pods/${POD}"
    mkdir -p "${POD_DIR}"

    # describe
    run_cmd "${POD_DIR}/describe.txt" \
      kubectl describe pod "${POD}" -n "${NS}"

    # 컨테이너 목록 추출
    CONTAINERS="$(kubectl get pod "${POD}" -n "${NS}" \
      -o jsonpath='{.spec.containers[*].name}' 2>/dev/null || true)"

    for CONTAINER in ${CONTAINERS}; do
      # 현재 로그
      run_cmd "${POD_DIR}/logs-${CONTAINER}.txt" \
        kubectl logs "${POD}" -n "${NS}" -c "${CONTAINER}" --tail="${LOG_LINES}"

      # 이전 컨테이너 로그 (CrashLoopBackOff 등 재시작된 경우)
      run_cmd "${POD_DIR}/logs-${CONTAINER}-previous.txt" \
        kubectl logs "${POD}" -n "${NS}" -c "${CONTAINER}" --tail="${LOG_LINES}" --previous
    done
  done

  log "[${NS}] 수집 완료."
done

# ── 마무리 ───────────────────────────────────────────────

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo " 수집 완료"
echo " 저장 위치: ${OUTPUT_DIR}"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "디렉토리 구조:"
find "${OUTPUT_DIR}" -type f | sort | sed "s|${OUTPUT_DIR}/||"
