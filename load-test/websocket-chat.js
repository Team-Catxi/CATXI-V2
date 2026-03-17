/**
 * k6 WebSocket(STOMP) 채팅 부하 테스트
 *
 * 목적: 200명 동시 STOMP 연결 + 메시지 전송 시 지연·실패율 측정
 *
 * 전제: SockJS의 raw WebSocket 엔드포인트 사용
 *   ws://{host}/connect/{serverId}/{sessionId}/websocket
 *
 * 실행:
 *   k6 run \
 *     --env WS_URL=wss://catxi.shop/connect \
 *     --env TOKEN=<jwt> \
 *     --env ROOM_ID=<roomId> \
 *     load-test/websocket-chat.js
 */
import ws from 'k6/ws';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import { randomString } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

const WS_BASE = __ENV.WS_URL  || 'wss://catxi.shop/connect';
const TOKEN   = __ENV.TOKEN   || '';
const ROOM_ID = __ENV.ROOM_ID || '1';

// 커스텀 메트릭
const msgSent       = new Counter('ws_messages_sent');
const msgReceived   = new Counter('ws_messages_received');
const connectFailed = new Rate('ws_connect_failed');
const msgDelay      = new Trend('ws_message_delay_ms');

export const options = {
  stages: [
    { duration: '30s', target: 50  },
    { duration: '1m',  target: 100 },
    { duration: '2m',  target: 200 },
    { duration: '2m',  target: 200 },
    { duration: '30s', target: 0   },
  ],
  thresholds: {
    ws_connect_failed:    ['rate<0.05'],        // 연결 실패율 5% 이하
    ws_message_delay_ms:  ['p(95)<1000'],       // 메시지 전달 p95 1초 이하
    ws_messages_received: ['count>100'],         // 최소 100건 수신
  },
};

// SockJS raw WebSocket URL 생성
function sockJsUrl() {
  const serverId  = Math.floor(Math.random() * 999).toString().padStart(3, '0');
  const sessionId = randomString(8);
  return `${WS_BASE}/${serverId}/${sessionId}/websocket`;
}

// STOMP 프레임 조립
function stompFrame(command, headers = {}, body = '') {
  let frame = `${command}\n`;
  for (const [k, v] of Object.entries(headers)) {
    frame += `${k}:${v}\n`;
  }
  frame += '\n' + body + '\0';
  return frame;
}

export default function () {
  const url = sockJsUrl();
  const sentAt = {};

  const res = ws.connect(url, {}, (socket) => {
    socket.on('open', () => {
      // STOMP CONNECT
      socket.send(stompFrame('CONNECT', {
        'accept-version': '1.1,1.2',
        'heart-beat':     '0,0',
        'Authorization':  `Bearer ${TOKEN}`,
      }));
    });

    socket.on('message', (data) => {
      // SockJS 오프닝 프레임 무시 ("o")
      if (data === 'o') return;

      // SockJS 메시지 프레임 파싱 ("a[\"...\"]")
      let payload = data;
      if (data.startsWith('a[')) {
        try { payload = JSON.parse(JSON.parse(data.slice(1))[0]); } catch {}
      }

      const payloadStr = typeof payload === 'string' ? payload : JSON.stringify(payload);

      if (payloadStr.startsWith('CONNECTED')) {
        // 채팅방 구독
        socket.send(stompFrame('SUBSCRIBE', {
          id:          'sub-0',
          destination: `/topic/${ROOM_ID}`,
        }));

        // 메시지 주기적 전송 (2초마다 최대 5회)
        let sendCount = 0;
        const interval = socket.setInterval(() => {
          if (sendCount >= 5) {
            socket.clearInterval(interval);
            return;
          }
          const msgId = `${__VU}-${sendCount}`;
          sentAt[msgId] = Date.now();
          socket.send(stompFrame('SEND', {
            destination:    `/publish/chat/${ROOM_ID}`,
            'content-type': 'application/json',
          }, JSON.stringify({
            roomId:  ROOM_ID,
            content: `VU ${__VU} message ${sendCount}`,
            type:    'CHAT',
          })));
          msgSent.add(1);
          sendCount++;
        }, 2000);

      } else if (payloadStr.includes(`/topic/${ROOM_ID}`)) {
        msgReceived.add(1);
        // 메시지 지연 측정 (같은 VU가 보낸 메시지 기준)
        const msgId = `${__VU}-${Math.floor(msgReceived.value - 1)}`;
        if (sentAt[msgId]) {
          msgDelay.add(Date.now() - sentAt[msgId]);
        }
      }
    });

    socket.on('error', () => {
      connectFailed.add(true);
    });

    socket.setTimeout(() => {
      socket.send(stompFrame('DISCONNECT', {}));
      socket.close();
    }, 15000); // 15초 후 연결 종료
  });

  const connected = check(res, { 'WS 연결 성공': (r) => r && r.status === 101 });
  connectFailed.add(!connected);

  sleep(1);
}
