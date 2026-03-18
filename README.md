# 🚕 Catxi Backend

가톨릭대학교 학생들을 위한 실시간 택시 합승 플랫폼의 백엔드 서비스입니다.

<img width="2000" height="1200" alt="image" src="https://github.com/user-attachments/assets/4ec1163c-5ad4-4660-851e-f84b1b70102d" />
<img width="2000" height="1200" alt="image" src="https://github.com/user-attachments/assets/2055e1fa-be82-463f-a7c1-35c24852aace" />


## 📋 개요

Catxi Backend는 가톨릭대학교 캠퍼스 근처에서 학생들의 안전하고 경제적인 택시 합승을 지원하는 RESTful API 서버입니다. 실시간 채팅, 위치 공유, 매칭 시스템을 제공하여 학생들이 편리하게 택시를 공유할 수 있도록 돕습니다.

## 🛠 기술 스택

### 코어 프레임워크

### 데이터베이스 & 캐시


### 실시간 통신


### 외부 서비스 연동

### 개발 & 운영 도구

## 🏗 아키텍처


## 📚 API 문서


### 모니터링


## 🔧 V2 개선 작업 이력

1. **CI/CD 자동화** — 수동 SSH 배포(`docker pull && docker run`)의 휴먼 에러·반복 비용 제거. main push → GitHub Actions → ECR 이미지 빌드/푸시 → SSM send-command → EC2 무중단 배포로 전환. SSH 키 관리 제거, IAM 기반 접근 제어.

2. **Prometheus + Grafana + Loki 모니터링 서버 구축** — 병목 지점 파악 수단이 없어 성능 개선 근거를 만들 수 없었음. 전용 모니터링 EC2에 Prometheus·Grafana·Loki를 Docker Compose로 구성. HTTP SLO 히스토그램(50ms~2s), JVM 힙, DB 커넥션풀 실시간 가시화. 로그는 Loki로 중앙 집약해 재배포 시 유실 제거.

3. **선택적 로깅 전략 적용** — 채팅 메시지·좌표 수신 시 payload 전체 INFO 출력이 Loki 스토리지 급증 위험. 비즈니스 이벤트(채팅방 생성·입장·퇴장, 준비·매칭 결과, 이력 저장)만 INFO로 남기고 나머지 제거. `System.out.println` 2개 → `log.debug` 교체.

4. **WebSocket 성능 튜닝** — k6로 200 VU 부하 테스트 실행 결과 ws_connecting p(95) 10.07s·연결 실패율 18% 확인. STOMP 인바운드/아웃바운드 채널 스레드 풀 증설(core=8, max=32), Lettuce Pub/Sub 커넥션풀 확장(maxTotal=20), JVM 힙 `-Xmx512m` → `-Xmx1g`(200 VU 동시 세션에서 OOM 재현)으로 조정.

5. **CORS 설정 복구 및 보완** — V2 초기화 시 `CorsConfig.java` 누락으로 프론트에서 JWT 관련 커스텀 헤더(`access`, `isNewUser`, `X-Access-Token-Refreshed`) 접근 불가. 파일 재생성 및 `exposedHeaders` 추가.

6. **EC2 탄력적 IP + Route53 → ALB 연결** — 재기동 시 IP 변동으로 인한 엔드포인트 불안정 제거. 탄력적 IP로 고정 퍼블릭 IP 확보, Route53 A 레코드로 `api.catxi.shop` → ALB alias 연결, ACM `*.catxi.shop` 와일드카드 인증서로 HTTPS 종단 처리.
