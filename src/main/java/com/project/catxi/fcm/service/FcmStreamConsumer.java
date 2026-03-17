package com.project.catxi.fcm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.catxi.fcm.dto.FcmNotificationEvent;
import com.project.catxi.member.domain.Member;
import com.project.catxi.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 Redis Streams에서 메시지를 읽어 FCM 발송
 * - Consumer Group으로 2대 서버에 자동 분배
 * - ACK로 메시지 처리 완료 보장
 * - 실패 시 재시도 및 DLQ 이동
 * - XCLAIM으로 장애 서버 메시지 인수
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FcmStreamConsumer {

    private static final int MAX_RETRY = 3;

    private final FcmStreamService fcmStreamService;
    private final FcmNotificationService fcmNotificationService;
    private final FcmActiveStatusService fcmActiveStatusService;
    private final MemberRepository memberRepository;
    private final ObjectMapper objectMapper;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private ExecutorService consumerExecutor;
    private String consumerName;

    /**
     * 애플리케이션 시작 시 Consumer 시작
     */
    @EventListener(ApplicationReadyEvent.class)
    public void startConsumer() {
        // 고유한 Consumer 이름 생성 (서버별 구분)
        consumerName = generateConsumerName();

        log.info("FCM Stream Consumer 시작: name={}", consumerName);
        running.set(true);

        // Consumer 스레드 풀 생성
        consumerExecutor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "FCM-Stream-Consumer");
            t.setDaemon(false);
            return t;
        });

        // 메시지 소비 스레드 시작
        consumerExecutor.submit(this::consumeLoop);

        // Pending 메시지 인수 스레드 시작 (30초마다)
        consumerExecutor.submit(this::claimLoop);

        log.info("FCM Stream Consumer 스레드 시작 완료");
    }

    /**
     * Consumer 이름 생성
     * - 형식: hostname-pid (예: server1-12345)
     * - 서버 장애 시 어떤 서버의 메시지인지 식별 가능
     */
    private String generateConsumerName() {
        try {
            String hostname = java.net.InetAddress.getLocalHost().getHostName();
            String pid = java.lang.management.ManagementFactory.getRuntimeMXBean()
                    .getName().split("@")[0];
            return String.format("%s-%s", hostname, pid);
        } catch (Exception e) {
            log.warn("Consumer 이름 생성 실패, UUID 사용", e);
            return "consumer-" + UUID.randomUUID().toString().substring(0, 8);
        }
    }

    /**
     * 메시지 소비 루프 (배치 ACK 적용)
     */
    private void consumeLoop() {
        log.info("FCM Consumer Loop 시작: {}", consumerName);

        while (running.get()) {
            try {
                // Redis Streams에서 메시지 읽기 (블로킹)
                List<MapRecord<String, Object, Object>> messages = fcmStreamService.consume(consumerName);

                if (messages == null || messages.isEmpty()) {
                    continue;
                }

                // 배치 처리: 성공한 메시지 ID 수집
                List<String> successfulMessageIds = new ArrayList<>();

                for (MapRecord<String, Object, Object> message : messages) {
                    boolean success = processMessage(message);
                    if (success) {
                        successfulMessageIds.add(message.getId().getValue());
                    }
                }

                // 배치 ACK: 성공한 메시지들을 한 번에 ACK
                if (!successfulMessageIds.isEmpty()) {
                    fcmStreamService.acknowledgeAll(successfulMessageIds);
                }

            } catch (Exception e) {
                if (running.get()) {
                    log.error("FCM Consumer Loop 오류", e);
                    sleep(1000);
                }
            }
        }

        log.info("FCM Consumer Loop 종료: {}", consumerName);
    }

    /**
     * 개별 메시지 처리
     * 
     * @return 처리 성공 여부 (배치 ACK에서 사용)
     */
    private boolean processMessage(MapRecord<String, Object, Object> message) {
        String messageId = message.getId().getValue();

        try {
            // payload 추출
            Object payloadObj = message.getValue().get("payload");
            if (payloadObj == null) {
                log.warn("payload가 없는 메시지: messageId={}", messageId);
                return true; // 빈 메시지도 ACK 대상
            }

            String payload = payloadObj.toString();
            FcmNotificationEvent event = objectMapper.readValue(payload, FcmNotificationEvent.class);

            log.debug("FCM 메시지 처리 시작: messageId={}, eventId={}", messageId, event.eventId());

            // FCM 발송
            boolean success = processNotification(event);

            if (success) {
                log.debug("FCM 처리 성공: messageId={}, eventId={}", messageId, event.eventId());
                return true; // 성공 → 배치 ACK 대상
            } else {
                // 처리 실패 → 재시도 또는 DLQ
                handleFailure(message, event, "FCM 발송 실패");
                return true; // 실패 처리 완료 → ACK (handleFailure에서 재발행 또는 DLQ 처리)
            }

        } catch (Exception e) {
            log.error("FCM 메시지 처리 중 예외: messageId={}", messageId, e);
            handleException(message, e);
            return true; // 예외 처리 완료 → ACK
        }
    }

    /**
     * FCM 알림 발송 처리
     */
    private boolean processNotification(FcmNotificationEvent event) {
        try {
            // 대상 사용자 조회
            List<Member> targetMembers = memberRepository.findAllById(event.targetMemberIds());

            if (targetMembers.isEmpty()) {
                log.warn("FCM 알림 대상 없음: eventId={}", event.eventId());
                return true; // 대상 없어도 성공으로 처리
            }

            // 알림 타입별 처리
            switch (event.type()) {
                case CHAT_MESSAGE:
                    return processChatNotification(event, targetMembers.get(0));
                case READY_REQUEST:
                    return processReadyRequestNotification(event, targetMembers);
                default:
                    log.warn("알 수 없는 알림 타입: {}", event.type());
                    return true;
            }

        } catch (Exception e) {
            log.error("FCM 알림 처리 실패: eventId={}", event.eventId(), e);
            return false;
        }
    }

    /**
     * 채팅 알림 처리
     */
    private boolean processChatNotification(FcmNotificationEvent event, Member targetMember) {
        Long roomId = extractRoomId(event);
        if (roomId == null) {
            log.warn("roomId 추출 실패: eventId={}", event.eventId());
            return true;
        }

        // 활성 상태 확인
        if (fcmActiveStatusService.isUserActiveInRoom(targetMember.getId(), roomId)) {
            log.debug("사용자 활성 상태 - 알림 스킵: memberId={}, roomId={}",
                    targetMember.getId(), roomId);
            return true;
        }

        // FCM 발송
        String[] bodyParts = event.body().split(": ", 2);
        String senderNickname = bodyParts.length > 0 ? bodyParts[0] : "Unknown";
        String message = bodyParts.length > 1 ? bodyParts[1] : event.body();

        fcmNotificationService.sendChatNotificationSync(targetMember, senderNickname, message);
        return true;
    }

    /**
     * 준비 요청 알림 처리
     */
    private boolean processReadyRequestNotification(FcmNotificationEvent event, List<Member> targetMembers) {
        Long roomId = extractRoomId(event);
        if (roomId == null) {
            log.warn("roomId 추출 실패: eventId={}", event.eventId());
            return true;
        }

        // 비활성 사용자만 필터링
        List<Member> inactiveMembers = targetMembers.stream()
                .filter(m -> !fcmActiveStatusService.isUserActiveInRoom(m.getId(), roomId))
                .toList();

        if (!inactiveMembers.isEmpty()) {
            fcmNotificationService.sendReadyRequestNotificationSync(inactiveMembers, roomId);
        }

        return true;
    }

    /**
     * roomId 추출
     */
    private Long extractRoomId(FcmNotificationEvent event) {
        try {
            String roomIdStr = event.data().get("roomId");
            return roomIdStr != null ? Long.parseLong(roomIdStr) : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 실패 처리 (재시도 또는 DLQ)
     * - 배치 ACK에서 처리하므로 개별 ACK 호출 불필요
     */
    private void handleFailure(MapRecord<String, Object, Object> message,
            FcmNotificationEvent event, String errorMessage) {

        if (event.retryCount() < MAX_RETRY) {
            // 재시도: retryCount 증가 후 다시 발행
            FcmNotificationEvent retryEvent = event.withIncrementedRetry();
            try {
                String payload = objectMapper.writeValueAsString(retryEvent);
                fcmStreamService.publish(payload);
                log.warn("FCM 재시도 예정: eventId={}, retry={}/{}",
                        event.eventId(), retryEvent.retryCount(), MAX_RETRY);
            } catch (Exception e) {
                log.error("FCM 재시도 발행 실패", e);
                fcmStreamService.moveToDlq(event, e.getMessage());
            }
        } else {
            // DLQ로 이동
            fcmStreamService.moveToDlq(event, errorMessage);
            log.error("FCM DLQ 이동: eventId={}, retry={}", event.eventId(), event.retryCount());
        }
    }

    /**
     * 예외 처리
     * - 배치 ACK에서 처리하므로 개별 ACK 호출 불필요
     */
    private void handleException(MapRecord<String, Object, Object> message, Exception e) {
        String messageId = message.getId().getValue();
        log.error("FCM 메시지 처리 예외: messageId={}", messageId);
    }

    /**
     * Pending 메시지 인수 루프 (30초마다)
     */
    private void claimLoop() {
        log.info("FCM Claim Loop 시작: {}", consumerName);

        while (running.get()) {
            try {
                sleep(30000); // 30초 대기

                if (running.get()) {
                    fcmStreamService.claimPendingMessages(consumerName);
                }

            } catch (Exception e) {
                if (running.get()) {
                    log.error("FCM Claim Loop 오류", e);
                }
            }
        }

        log.info("FCM Claim Loop 종료: {}", consumerName);
    }

    /**
     * Sleep 유틸
     */
    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 종료 처리
     */
    @PreDestroy
    public void stopConsumer() {
        log.info("FCM Stream Consumer 종료 시작: {}", consumerName);
        running.set(false);

        if (consumerExecutor != null) {
            consumerExecutor.shutdown();
            try {
                if (!consumerExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                    consumerExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                consumerExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        log.info("FCM Stream Consumer 종료 완료: {}", consumerName);
    }
}
