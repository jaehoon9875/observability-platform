import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

// 목표: Alert Rule (HighErrorRate, HighP99Latency) 트리거 검증
const errorRate = new Rate('order_error_rate');

// 환경변수로 부하 설정 제어 (기본값: 홈서버 기준)
const BASE_VUS  = parseInt(__ENV.BASE_VUS)  || 5;
const SPIKE_VUS = parseInt(__ENV.SPIKE_VUS) || 20;
const SLEEP_SEC = parseFloat(__ENV.SLEEP)   || 0.3;

export const options = {
  stages: [
    { duration: '1m',  target: BASE_VUS  },  // 기준 트래픽 (워밍업)
    { duration: '30s', target: SPIKE_VUS },  // 급증
    { duration: '2m',  target: SPIKE_VUS },  // 급증 유지 — Alert 트리거 대기 (for: 2m)
    { duration: '30s', target: BASE_VUS  },  // 회복
    { duration: '1m',  target: BASE_VUS  },  // 기준 트래픽 유지 (Alert 해소 확인)
  ],
  // spike-test는 SLO 위반이 목표이므로 thresholds를 느슨하게 설정
  thresholds: {
    http_req_failed: ['rate<0.5'],  // 50% 이상 실패 시에만 테스트 자체를 abort
  },
};

const BASE_URL    = __ENV.BASE_URL || 'http://order-service:8080';
const PRODUCTS    = ['1', '2', '3', '4', '5'];
const UNIT_PRICES = [5000, 10000, 15000, 20000, 30000];

function randomItem(arr) {
  return arr[Math.floor(Math.random() * arr.length)];
}

export default function () {
  const quantity  = Math.floor(Math.random() * 5) + 1;
  const unitPrice = randomItem(UNIT_PRICES);
  const payload = JSON.stringify({
    productId:   randomItem(PRODUCTS),
    quantity:    quantity,
    totalAmount: unitPrice * quantity,
  });

  const params = {
    headers: { 'Content-Type': 'application/json' },
    timeout: '15s',
  };

  const res = http.post(`${BASE_URL}/api/orders`, payload, params);

  const success = check(res, {
    'status is 2xx': (r) => r.status >= 200 && r.status < 300,
  });

  errorRate.add(!success);

  sleep(SLEEP_SEC);
}
