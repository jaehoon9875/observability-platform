import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

// 커스텀 메트릭
const errorRate = new Rate('order_error_rate');
const orderDuration = new Trend('order_duration_ms');

// 환경변수로 부하 설정 제어 (기본값: 홈서버 기준)
const MAX_VUS      = parseInt(__ENV.MAX_VUS)   || 10;
const SLEEP_SEC    = parseFloat(__ENV.SLEEP)   || 1.0;
const RAMPUP_DUR   = __ENV.RAMPUP_DUR          || '1m';
const SUSTAIN_DUR  = __ENV.SUSTAIN_DUR         || '3m';
const RAMPDOWN_DUR = __ENV.RAMPDOWN_DUR        || '1m';

export const options = {
  stages: [
    { duration: RAMPUP_DUR,   target: MAX_VUS },  // ramp-up
    { duration: SUSTAIN_DUR,  target: MAX_VUS },  // 유지
    { duration: RAMPDOWN_DUR, target: 0 },         // ramp-down
  ],
  thresholds: {
    // SLO 기준: 가용성 99.9%, P99 레이턴시 500ms
    http_req_failed:   ['rate<0.001'],
    http_req_duration: ['p(99)<500'],
    order_error_rate:  ['rate<0.001'],
  },
};

const BASE_URL    = __ENV.BASE_URL || 'http://order-service:8080';
const PRODUCTS    = ['1', '2', '3', '4', '5'];
const UNIT_PRICES = [5000, 10000, 15000, 20000, 30000];

function randomItem(arr) {
  return arr[Math.floor(Math.random() * arr.length)];
}

export default function () {
  const quantity  = Math.floor(Math.random() * 3) + 1;
  const unitPrice = randomItem(UNIT_PRICES);
  const payload = JSON.stringify({
    productId:   randomItem(PRODUCTS),
    quantity:    quantity,
    totalAmount: unitPrice * quantity,
  });

  const params = {
    headers: { 'Content-Type': 'application/json' },
    timeout: '10s',
  };

  const res = http.post(`${BASE_URL}/api/orders`, payload, params);

  const success = check(res, {
    'status is 200 or 201': (r) => r.status === 200 || r.status === 201,
    'response time < 500ms': (r) => r.timings.duration < 500,
  });

  errorRate.add(!success);
  orderDuration.add(res.timings.duration);

  sleep(SLEEP_SEC);
}
