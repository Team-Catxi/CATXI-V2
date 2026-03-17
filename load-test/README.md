# k6 부하 테스트

## 사전 준비

```bash
# k6 설치
brew install k6

# JWT 토큰 발급 (로그인 후 복사)
TOKEN=$(curl -s -X POST https://catxi.shop/api/members/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@cuk.ac.kr","password":"..."}' | jq -r '.accessToken')
```

## 테스트 실행

### 1. REST API 부하 테스트 (Phase 2 메인)

```bash
k6 run \
  --env BASE_URL=https://catxi.shop \
  --env TOKEN=$TOKEN \
  --env ROOM_ID=<실제 채팅방 ID> \
  load-test/rest-api.js
```

| 단계 | VU | 시간 |
|------|-----|------|
| 워밍업 | 0→50 | 1분 |
| 증가 | 50→100 | 2분 |
| 최대 | 100→200 | 3분 |
| 유지 | 200 | 2분 |
| 종료 | →0 | 1분 |

### 2. WebSocket STOMP 채팅 부하 테스트

```bash
k6 run \
  --env WS_URL=wss://catxi.shop/connect \
  --env TOKEN=$TOKEN \
  --env ROOM_ID=<채팅방 ID> \
  load-test/websocket-chat.js
```

### 3. 스파이크 테스트

```bash
k6 run \
  --env BASE_URL=https://catxi.shop \
  --env TOKEN=$TOKEN \
  load-test/spike.js
```

## Grafana 연동 (권장)

테스트 중 실시간으로 Grafana에서 메트릭을 확인하려면 InfluxDB를 경유합니다.

```bash
# InfluxDB 로컬 실행
docker run -d -p 8086:8086 \
  -e DOCKER_INFLUXDB_INIT_MODE=setup \
  -e DOCKER_INFLUXDB_INIT_USERNAME=admin \
  -e DOCKER_INFLUXDB_INIT_PASSWORD=adminpass \
  -e DOCKER_INFLUXDB_INIT_ORG=catxi \
  -e DOCKER_INFLUXDB_INIT_BUCKET=k6 \
  influxdb:2

# k6 실행 시 InfluxDB로 결과 전송
k6 run --out influxdb=http://localhost:8086/k6 \
  --env BASE_URL=https://catxi.shop \
  --env TOKEN=$TOKEN \
  load-test/rest-api.js
```

## 결과 저장

각 스크립트는 `load-test/results/` 디렉토리에 요약 파일을 생성합니다.
결과는 `PORTFOLIO.md` Phase 2 섹션에 기록합니다.
