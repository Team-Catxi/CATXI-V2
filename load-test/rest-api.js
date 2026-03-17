/**
 * k6 REST API 부하 테스트
 *
 * 목적: 200명 동시 접속 시 REST API 응답 시간·에러율 측정
 * 실행: k6 run --env BASE_URL=https://catxi.shop --env TOKEN=<jwt> load-test/rest-api.js
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'https://catxi.shop';
const TOKEN    = __ENV.TOKEN    || '';

// 커스텀 메트릭
const errorRate       = new Rate('error_rate');
const chatRoomLatency = new Trend('chat_room_list_duration');
const msgHistoryLatency = new Trend('msg_history_duration');

export const options = {
  stages: [
    { duration: '1m',  target: 50  },  // 0 → 50 VU (워밍업)
    { duration: '2m',  target: 100 },  // 50 → 100 VU
    { duration: '3m',  target: 200 },  // 100 → 200 VU (최대 부하)
    { duration: '2m',  target: 200 },  // 200 VU 유지
    { duration: '1m',  target: 0   },  // 종료
  ],
  thresholds: {
    // 95%ile 응답시간 2초 이하
    'http_req_duration{name:chat_rooms}': ['p(95)<2000'],
    'http_req_duration{name:msg_history}': ['p(95)<2000'],
    'http_req_duration{name:health}': ['p(95)<500'],
    // 에러율 1% 이하
    error_rate: ['rate<0.01'],
    // 전체 HTTP 실패율 1% 이하
    http_req_failed: ['rate<0.01'],
  },
};

const headers = {
  'Content-Type': 'application/json',
  'Authorization': `Bearer ${TOKEN}`,
};

export default function () {
  // 1) 헬스체크
  const health = http.get(`${BASE_URL}/actuator/health`, {
    tags: { name: 'health' },
  });
  check(health, { 'health 200': (r) => r.status === 200 });
  errorRate.add(health.status !== 200);

  sleep(0.5);

  // 2) 채팅방 목록 조회
  const rooms = http.get(`${BASE_URL}/chat/rooms`, {
    headers,
    tags: { name: 'chat_rooms' },
  });
  chatRoomLatency.add(rooms.timings.duration);
  const roomsOk = check(rooms, {
    'rooms 200': (r) => r.status === 200,
    'rooms has data': (r) => {
      try { return JSON.parse(r.body) !== null; } catch { return false; }
    },
  });
  errorRate.add(!roomsOk);

  sleep(1);

  // 3) 메시지 히스토리 조회 (채팅방 1번 가정 — 실제 roomId로 교체)
  const TEST_ROOM_ID = __ENV.ROOM_ID || '1';
  const msgs = http.get(`${BASE_URL}/chat/${TEST_ROOM_ID}/messages?page=0&size=20`, {
    headers,
    tags: { name: 'msg_history' },
  });
  msgHistoryLatency.add(msgs.timings.duration);
  const msgsOk = check(msgs, {
    'messages 200 or 404': (r) => r.status === 200 || r.status === 404,
  });
  errorRate.add(!msgsOk);

  sleep(1);
}

export function handleSummary(data) {
  const p50  = data.metrics['http_req_duration']?.values?.['p(50)']?.toFixed(1)  ?? '-';
  const p95  = data.metrics['http_req_duration']?.values?.['p(95)']?.toFixed(1)  ?? '-';
  const p99  = data.metrics['http_req_duration']?.values?.['p(99)']?.toFixed(1)  ?? '-';
  const rps  = data.metrics['http_reqs']?.values?.['rate']?.toFixed(2)           ?? '-';
  const errR = (data.metrics['error_rate']?.values?.['rate'] * 100)?.toFixed(2)  ?? '-';

  const summary = `
=== REST API 부하 테스트 결과 ===
날짜: ${new Date().toISOString()}
BASE_URL: ${BASE_URL}

[응답 시간]
  p50  : ${p50} ms
  p95  : ${p95} ms
  p99  : ${p99} ms

[처리량]
  RPS  : ${rps} req/s

[에러율]
  오류 : ${errR} %
================================
`;
  console.log(summary);

  return {
    'load-test/results/rest-api-summary.txt': summary,
  };
}
