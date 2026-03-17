/**
 * k6 인프라 기본 성능 베이스라인 테스트 (인증 불필요)
 *
 * 목적: JWT 없이도 측정 가능한 공개 엔드포인트로 서버·ALB 처리 성능 확인
 * 실행: k6 run --env BASE_URL=https://catxi.shop load-test/infra-baseline.js
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'https://catxi-alb-22006738.ap-northeast-2.elb.amazonaws.com';
const HOST     = __ENV.HOST     || 'catxi.shop';

const errorRate    = new Rate('error_rate');
const healthTrend  = new Trend('health_duration');
const promTrend    = new Trend('prometheus_duration');

export const options = {
  stages: [
    { duration: '30s', target: 50  },  // 0 → 50 VU
    { duration: '1m',  target: 100 },  // 100 VU 유지
    { duration: '1m',  target: 200 },  // 200 VU 최대 부하
    { duration: '30s', target: 0   },  // 종료
  ],
  thresholds: {
    'http_req_duration{name:health}':     ['p(95)<500'],
    'http_req_duration{name:prometheus}': ['p(95)<2000'],
    error_rate:      ['rate<0.01'],
    http_req_failed: ['rate<0.01'],
  },
};

const params = {
  headers: { Host: HOST },
  tags: {},
};

export default function () {
  // 1) Health check
  const h = http.get(`${BASE_URL}/actuator/health`, {
    headers: { Host: HOST },
    tags: { name: 'health' },
  });
  healthTrend.add(h.timings.duration);
  const hOk = check(h, { 'health UP': (r) => r.status === 200 });
  errorRate.add(!hOk);

  sleep(0.5);

  // 2) Prometheus 메트릭 엔드포인트 (scrape 성능 측정)
  const p = http.get(`${BASE_URL}/actuator/prometheus`, {
    headers: { Host: HOST },
    tags: { name: 'prometheus' },
  });
  promTrend.add(p.timings.duration);
  const pOk = check(p, { 'prometheus 200': (r) => r.status === 200 });
  errorRate.add(!pOk);

  sleep(1);
}

export function handleSummary(data) {
  const hP50  = data.metrics['health_duration']?.values?.['p(50)']?.toFixed(1)      ?? '-';
  const hP95  = data.metrics['health_duration']?.values?.['p(95)']?.toFixed(1)      ?? '-';
  const hP99  = data.metrics['health_duration']?.values?.['p(99)']?.toFixed(1)      ?? '-';
  const pP50  = data.metrics['prometheus_duration']?.values?.['p(50)']?.toFixed(1)  ?? '-';
  const pP95  = data.metrics['prometheus_duration']?.values?.['p(95)']?.toFixed(1)  ?? '-';
  const rps   = data.metrics['http_reqs']?.values?.['rate']?.toFixed(2)             ?? '-';
  const errR  = ((data.metrics['error_rate']?.values?.['rate'] ?? 0) * 100).toFixed(2);

  const summary = `
=== 인프라 기본 성능 베이스라인 결과 ===
날짜: ${new Date().toISOString()}
최대 VU: 200명

[Health Check (/actuator/health)]
  p50 : ${hP50} ms
  p95 : ${hP95} ms
  p99 : ${hP99} ms

[Prometheus Scrape (/actuator/prometheus)]
  p50 : ${pP50} ms
  p95 : ${pP95} ms

[종합]
  RPS    : ${rps} req/s
  에러율 : ${errR} %
=========================================
`;
  console.log(summary);
  return {
    'load-test/results/infra-baseline-summary.txt': summary,
  };
}
