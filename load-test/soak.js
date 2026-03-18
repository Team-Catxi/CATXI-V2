/**
 * k6 Soak Test (내구성 테스트)
 *
 * 목적: 장시간(40분) 100 VU 고정 부하 유지
 *       → 메모리 누수, 커넥션풀 고갈, 성능 점진적 저하 감지
 *
 * 각 VU 행동:
 *   - WebSocket(STOMP) 연결을 5분간 유지하며 20초마다 메시지 전송
 *   - WS 세션 종료 후 REST API (채팅방 목록) 조회 1회
 *   - 루프 반복 (40분 동안 계속)
 *
 * 실행:
 *   k6 run \
 *     --env WS_URL=wss://api.catxi.shop/connect \
 *     --env BASE_URL=https://api.catxi.shop \
 *     --env TOKEN=<jwt> \
 *     --env ROOM_ID=<roomId> \
 *     load-test/soak.js
 *
 * Grafana 동시 모니터링 패널:
 *   1. JVM Heap Used              → 우상향 추세 = 메모리 누수
 *   2. GC Pause Duration          → 후반 증가 = 힙 압박
 *   3. HikariCP Active/Pending    → Pending 발생 = 커넥션풀 고갈
 *   4. HTTP p95 Latency 추세      → 후반 상승 = 점진적 성능 저하
 *   5. JVM Thread Count           → 지속 증가 = 스레드 누수
 *   6. Process CPU Usage          → 특정 구간 급등 확인
 */
import ws   from 'k6/ws';
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import { randomString } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

const WS_BASE  = __ENV.WS_URL   || 'wss://api.catxi.shop/connect';
const BASE_URL = __ENV.BASE_URL || 'https://api.catxi.shop';
const TOKEN    = __ENV.TOKEN    || '';
const ROOM_ID  = __ENV.ROOM_ID  || '1';

// ── 커스텀 메트릭 ──────────────────────────────────────────────────────────
const wsMsgSent      = new Counter('soak_ws_messages_sent');
const wsMsgReceived  = new Counter('soak_ws_messages_received');
const wsConnFailed   = new Rate('soak_ws_connect_failed');
const wsMsgDelay     = new Trend('soak_ws_message_delay_ms');
const restErrorRate  = new Rate('soak_rest_error_rate');
const restLatency    = new Trend('soak_rest_latency_ms');

// ── 테스트 설계 ────────────────────────────────────────────────────────────
// 워밍업 2분 → 100 VU 유지 36분 → 종료 2분 = 총 40분
export const options = {
  stages: [
    { duration: '2m',  target: 100 },  // 0 → 100 VU (워밍업)
    { duration: '36m', target: 100 },  // 100 VU 고정 유지 (핵심 구간)
    { duration: '2m',  target: 0   },  // 종료
  ],
  thresholds: {
    soak_ws_connect_failed:   ['rate<0.02'],   // WS 연결 실패율 2% 이하
    soak_ws_message_delay_ms: ['p(95)<2000'],  // 메시지 전달 p95 2초 이하
    soak_rest_error_rate:     ['rate<0.01'],   // REST 에러율 1% 이하
    soak_rest_latency_ms:     ['p(95)<1000'],  // REST p95 1초 이하
  },
};

// ── 유틸 ───────────────────────────────────────────────────────────────────
function sockJsUrl() {
  const serverId  = Math.floor(Math.random() * 999).toString().padStart(3, '0');
  const sessionId = randomString(8);
  return `${WS_BASE}/${serverId}/${sessionId}/websocket`;
}

function stompFrame(command, headers = {}, body = '') {
  let frame = `${command}\n`;
  for (const [k, v] of Object.entries(headers)) frame += `${k}:${v}\n`;
  return frame + '\n' + body + '\0';
}

// ── 메인 VU 루프 ───────────────────────────────────────────────────────────
export default function () {
  const sentAt = {};

  // ① WebSocket 세션: 5분간 연결 유지
  const res = ws.connect(sockJsUrl(), {}, (socket) => {

    socket.on('open', () => {
      socket.send(stompFrame('CONNECT', {
        'accept-version': '1.1,1.2',
        'heart-beat':     '10000,10000',  // 10초 heartbeat (연결 유지 핵심)
        'Authorization':  `Bearer ${TOKEN}`,
      }));
    });

    socket.on('message', (data) => {
      if (data === 'o') return;

      let payload = data;
      if (data.startsWith('a[')) {
        try { payload = JSON.parse(JSON.parse(data.slice(1))[0]); } catch (_) {}
      }
      const payloadStr = typeof payload === 'string' ? payload : JSON.stringify(payload);

      if (payloadStr.startsWith('CONNECTED')) {
        // 채팅방 구독
        socket.send(stompFrame('SUBSCRIBE', {
          id:          'sub-chat',
          destination: `/topic/${ROOM_ID}`,
        }));

        // 20초마다 메시지 전송 (연결 유지 + 메모리 누수 유발 조건 생성)
        let sendCount = 0;
        socket.setInterval(() => {
          const msgId = `${__VU}-${sendCount}`;
          sentAt[msgId] = Date.now();
          socket.send(stompFrame('SEND', {
            destination:    `/publish/chat/${ROOM_ID}`,
            'content-type': 'application/json',
          }, JSON.stringify({
            roomId:  ROOM_ID,
            content: `soak VU${__VU} msg${sendCount}`,
            type:    'CHAT',
          })));
          wsMsgSent.add(1);
          sendCount++;
        }, 20000);

      } else if (payloadStr.includes(`/topic/${ROOM_ID}`)) {
        wsMsgReceived.add(1);
        // 메시지 딜리버리 지연 측정
        const idx   = wsMsgReceived.value - 1;
        const msgId = `${__VU}-${Math.floor(idx)}`;
        if (sentAt[msgId]) {
          wsMsgDelay.add(Date.now() - sentAt[msgId]);
          delete sentAt[msgId];  // 참조 해제 (누수 방지)
        }
      }
    });

    socket.on('error', () => wsConnFailed.add(true));

    // 5분 후 정상 종료 (VU가 루프를 돌며 재연결 — 40분간 반복)
    socket.setTimeout(() => {
      socket.send(stompFrame('DISCONNECT', {}));
      socket.close();
    }, 300000);
  });

  const connected = check(res, { 'WS 연결 성공': (r) => r && r.status === 101 });
  wsConnFailed.add(!connected);

  // ② REST API: WS 세션 종료 직후 채팅방 목록 조회
  //    (WS와 REST가 HikariCP 풀을 공유하는지 확인하는 포인트)
  const roomsRes = http.get(`${BASE_URL}/chat/rooms`, {
    headers: {
      'Authorization': `Bearer ${TOKEN}`,
      'Content-Type':  'application/json',
    },
    tags: { name: 'soak_rooms' },
  });
  restLatency.add(roomsRes.timings.duration);
  restErrorRate.add(roomsRes.status !== 200);
  check(roomsRes, { 'rooms 200': (r) => r.status === 200 });

  sleep(2);
}

// ── 결과 요약 ──────────────────────────────────────────────────────────────
export function handleSummary(data) {
  const fmt = (key, sub) => data.metrics[key]?.values?.[sub]?.toFixed(1) ?? '-';
  const pct = (key, sub) => ((data.metrics[key]?.values?.[sub] ?? 0) * 100).toFixed(2);

  const summary = `
=== Soak Test 결과 (40분 내구성) ===
날짜: ${new Date().toISOString()}
환경: ${BASE_URL}, 100 VU 고정

[WebSocket]
  연결 실패율        : ${pct('soak_ws_connect_failed', 'rate')} %
  메시지 전달 p50    : ${fmt('soak_ws_message_delay_ms', 'p(50)')} ms
  메시지 전달 p95    : ${fmt('soak_ws_message_delay_ms', 'p(95)')} ms
  메시지 전달 p99    : ${fmt('soak_ws_message_delay_ms', 'p(99)')} ms
  총 수신 메시지     : ${data.metrics['soak_ws_messages_received']?.values?.['count'] ?? 0} 건

[REST API (병행)]
  채팅방 목록 p95    : ${fmt('soak_rest_latency_ms', 'p(95)')} ms
  에러율             : ${pct('soak_rest_error_rate', 'rate')} %

[Grafana 확인 체크리스트]
  □ JVM Heap Used 전체 타임라인 캡처
  □ GC Pause Duration 전체 타임라인 캡처
  □ HikariCP Active + Pending 전체 타임라인 캡처
  □ HTTP p95 Latency 전반(0~10분) vs 후반(30~40분) 비교 캡처
  □ JVM Thread Count 전체 타임라인 캡처
  □ 이상 발견 시 원인 추적 및 수정 후 재테스트
========================================
`;
  console.log(summary);
  return { 'load-test/results/soak-summary.txt': summary };
}
