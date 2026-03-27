import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

// 목표: Alert Rule (HighErrorRate, HighP99Latency) 트리거 검증
const errorRate = new Rate('order_error_rate');

export const options = {
  // 급증 시나리오: 기준 트래픽 → 갑작스러운 스파이크 → 회복
  stages: [
    { duration: '1m',  target: 5  },  // 기준 트래픽 (워밍업)
    { duration: '30s', target: 20 },  // 급증 (4배)
    { duration: '2m',  target: 20 },  // 급증 유지 — Alert 트리거 대기 (for: 2m)
    { duration: '30s', target: 5  },  // 회복
    { duration: '1m',  target: 5  },  // 기준 트래픽 유지 (Alert 해소 확인)
  ],
  // spike-test는 SLO 위반이 목표이므로 thresholds를 느슨하게 설정
  thresholds: {
    http_req_failed: ['rate<0.5'],  // 50% 이상 실패 시에만 테스트 자체를 abort
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://order-service:8080';

const CUSTOMERS = [1001, 1002, 1003, 1004, 1005];
const PRODUCTS  = [1, 2, 3, 4, 5];

function randomItem(arr) {
  return arr[Math.floor(Math.random() * arr.length)];
}

export default function () {
  const payload = JSON.stringify({
    productId:  randomItem(PRODUCTS),
    quantity:   Math.floor(Math.random() * 5) + 1,
    customerId: randomItem(CUSTOMERS),
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

  sleep(0.3);
}
