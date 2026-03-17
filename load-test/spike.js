/**
 * k6 스파이크 테스트
 *
 * 목적: 0 → 200명 급증 시 EC2 CPU·메모리 및 응답 시간 반응 측정
 *       (정상 상태 복구 시간도 함께 측정)
 *
 * 실행:
 *   k6 run --env BASE_URL=https://catxi.shop --env TOKEN=<jwt> load-test/spike.js
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'https://catxi.shop';
const TOKEN    = __ENV.TOKEN    || '';

const errorRate = new Rate('spike_error_rate');

export const options = {
  stages: [
    { duration: '30s', target: 10  },   // 정상 기준선
    { duration: '10s', target: 200 },   // 급증 (스파이크)
    { duration: '2m',  target: 200 },   // 최대 부하 유지
    { duration: '10s', target: 10  },   // 급감
    { duration: '2m',  target: 10  },   // 복구 모니터링
    { duration: '10s', target: 0   },   // 종료
  ],
  thresholds: {
    // 스파이크 중 에러율 5% 이하 허용
    spike_error_rate: ['rate<0.05'],
    // 스파이크 중 p99 응답 시간 5초 이하
    'http_req_duration{scenario:default}': ['p(99)<5000'],
  },
};

const headers = {
  'Content-Type':  'application/json',
  'Authorization': `Bearer ${TOKEN}`,
};

export default function () {
  // 헬스 체크
  const health = http.get(`${BASE_URL}/actuator/health`, {
    tags: { name: 'spike_health' },
  });
  check(health, { '헬스 200': (r) => r.status === 200 });
  errorRate.add(health.status !== 200);

  sleep(0.2);

  // 채팅방 목록 (읽기 집중)
  const rooms = http.get(`${BASE_URL}/chat/rooms`, {
    headers,
    tags: { name: 'spike_rooms' },
  });
  const ok = check(rooms, { '목록 200': (r) => r.status === 200 });
  errorRate.add(!ok);

  sleep(0.3);
}

export function handleSummary(data) {
  const spikeP99 = data.metrics['http_req_duration']?.values?.['p(99)']?.toFixed(1) ?? '-';
  const errRate  = (data.metrics['spike_error_rate']?.values?.['rate'] * 100)?.toFixed(2) ?? '-';
  const reqs     = data.metrics['http_reqs']?.values?.['count'] ?? '-';

  const summary = `
=== 스파이크 테스트 결과 ===
날짜: ${new Date().toISOString()}
BASE_URL: ${BASE_URL}

[응답 시간]
  p99 : ${spikeP99} ms

[안정성]
  에러율  : ${errRate} %
  총 요청 : ${reqs}

[분석 포인트]
  - Grafana에서 스파이크 구간(~t+40s) EC2 CPU 사용률 확인
  - 스파이크 종료 후 응답 시간 정상 복구 시간 측정
===========================
`;
  console.log(summary);

  return {
    'load-test/results/spike-summary.txt': summary,
  };
}
