import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

// 커스텀 메트릭
const errorRate = new Rate('order_error_rate');
const orderDuration = new Trend('order_duration_ms');

export const options = {
  // 정상 트래픽: 점진적 증가 → 유지 → 감소
  stages: [
    { duration: '1m', target: 10 },  // ramp-up
    { duration: '3m', target: 10 },  // 유지
    { duration: '1m', target: 0 },   // ramp-down
  ],
  thresholds: {
    // SLO 기준: 가용성 99.9%, P99 레이턴시 500ms
    http_req_failed:                  ['rate<0.001'],
    'http_req_duration{p(99)}':       ['p(99)<500'],
    order_error_rate:                 ['rate<0.001'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://order-service:8080';

// 테스트 데이터
const PRODUCTS     = ['1', '2', '3', '4', '5'];
const UNIT_PRICES  = [5000, 10000, 15000, 20000, 30000];

function randomItem(arr) {
  return arr[Math.floor(Math.random() * arr.length)];
}

export default function () {
  const quantity   = Math.floor(Math.random() * 3) + 1;
  const unitPrice  = randomItem(UNIT_PRICES);
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

  sleep(1);
}
