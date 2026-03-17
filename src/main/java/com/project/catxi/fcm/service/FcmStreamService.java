package com.project.catxi.fcm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.catxi.fcm.dto.FcmNotificationEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 Redis Streams를 이용한 메시지 발행/소비
 * - XADD: 메시지 발행
 * - XREADGROUP: Consumer Group에서 메시지 읽기
 * - XACK: 처리 완료 확인
 * - XCLAIM: 장애 서버 메시지 인수
 */
@Slf4j
@Service
public class FcmStreamService {

    private static final String STREAM_KEY = "fcm:stream";
    private static final String DLQ_STREAM_KEY = "fcm:dlq";
    private static final String CONSUMER_GROUP = "fcm-consumers";
    private static final int READ_COUNT = 10; // 한 번에 읽을 메시지 수
    private static final long CLAIM_MIN_IDLE_TIME_MS = 30000; // 30초

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public FcmStreamService(@Qualifier("chatPubSub") StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 메시지 발행 (단순 버전 - 중복 체크 없음)
     * 
     * @param payload FcmNotificationEvent의 JSON 문자열
     * @return 메시지 ID (예: "1704000000000-0")
     */
    public String publish(String payload) {
        try {
            RecordId recordId = redisTemplate.opsForStream()
                    .add(STREAM_KEY, Map.of("payload", payload));

            String messageId = recordId.getValue();
            log.debug("FCM Stream 메시지 발행: messageId={}", messageId);
            return messageId;

        } catch (Exception e) {
            log.error("FCM Stream 메시지 발행 실패: payload={}", payload, e);
            throw new RuntimeException("Stream 발행 실패", e);
        }
    }

    /**
     * Consumer Group에서 메시지 읽기
     * 
     * @param consumerName 컨슈머 이름
     * @return 읽은 메시지 목록
     */
    public List<MapRecord<String, Object, Object>> consume(String consumerName) {
        try {
            // XREADGROUP GROUP fcm-consumers server1 COUNT 10 BLOCK 2000 STREAMS fcm:stream
            // >
            List<MapRecord<String, Object, Object>> messages = redisTemplate.opsForStream().read(
                    Consumer.from(CONSUMER_GROUP, consumerName),
                    StreamReadOptions.empty()
                            .count(READ_COUNT)
                            .block(Duration.ofSeconds(2)), // 2초 블로킹
                    StreamOffset.create(STREAM_KEY, ReadOffset.lastConsumed()));

            if (messages != null && !messages.isEmpty()) {
                log.debug("FCM Stream 메시지 읽기: consumer={}, count={}",
                        consumerName, messages.size());
            }

            return messages;

        } catch (Exception e) {
            log.error("FCM Stream 메시지 읽기 실패: consumer={}", consumerName, e);
            return List.of();
        }
    }

    /**
     * 처리 완료 확인 (ACK) - 단일 메시지
     * 
     * @param messageId 메시지 ID
     */
    public void acknowledge(String messageId) {
        try {
            // XACK fcm:stream fcm-consumers 1704000000000-0
            Long ackCount = redisTemplate.opsForStream()
                    .acknowledge(STREAM_KEY, CONSUMER_GROUP, messageId);

            log.debug("FCM Stream ACK: messageId={}, ackCount={}", messageId, ackCount);

        } catch (Exception e) {
            log.error("FCM Stream ACK 실패: messageId={}", messageId, e);
        }
    }

    /**
     * 배치 ACK - 여러 메시지를 한 번에 처리
     * 
     * @param messageIds 메시지 ID 목록
     * @return ACK된 메시지 수
     */
    public long acknowledgeAll(List<String> messageIds) {
        if (messageIds == null || messageIds.isEmpty()) {
            return 0;
        }

        try {
            // XACK는 가변 인수를 받을 수 있음
            String[] ids = messageIds.toArray(new String[0]);
            Long ackCount = redisTemplate.opsForStream()
                    .acknowledge(STREAM_KEY, CONSUMER_GROUP, ids);

            log.info("FCM Stream 배치 ACK: count={}/{}", ackCount, messageIds.size());
            return ackCount != null ? ackCount : 0;

        } catch (Exception e) {
            log.error("FCM Stream 배치 ACK 실패: count={}", messageIds.size(), e);
            return 0;
        }
    }

    /**
     * DLQ로 메시지 이동
     * 
     * @param event        FCM 이벤트
     * @param errorMessage 에러 메시지
     */
    public void moveToDlq(FcmNotificationEvent event, String errorMessage) {
        try {
            Map<String, String> dlqData = Map.of(
                    "event", objectMapper.writeValueAsString(event),
                    "error", errorMessage,
                    "failedAt", String.valueOf(System.currentTimeMillis()));

            RecordId recordId = redisTemplate.opsForStream()
                    .add(DLQ_STREAM_KEY, dlqData);

            log.warn("FCM DLQ 이동: eventId={}, messageId={}, error={}",
                    event.eventId(), recordId.getValue(), errorMessage);

        } catch (Exception e) {
            log.error("FCM DLQ 이동 실패: eventId={}", event.eventId(), e);
        }
    }

    /**
     * Pending 메시지 조회 및 인수 (XCLAIM)
     * - 30초 이상 처리되지 않은 메시지 찾기
     * - 현재 Consumer로 소유권 변경
     * 
     * @param consumerName 현재 컨슈머 이름
     */
    public void claimPendingMessages(String consumerName) {
        try {
            // XPENDING fcm:stream fcm-consumers - + 30
            PendingMessages pendingMessages = redisTemplate.opsForStream()
                    .pending(STREAM_KEY, CONSUMER_GROUP,
                            Range.unbounded(), 30L);

            if (pendingMessages == null || pendingMessages.isEmpty()) {
                return;
            }

            log.debug("FCM Pending 메시지 발견: count={}", pendingMessages.size());

            // 30초 이상 처리되지 않은 메시지만 필터링
            for (PendingMessage pm : pendingMessages) {
                if (pm.getElapsedTimeSinceLastDelivery().toMillis() > CLAIM_MIN_IDLE_TIME_MS) {
                    claimMessage(consumerName, pm.getIdAsString());
                }
            }

        } catch (Exception e) {
            log.error("FCM Pending 메시지 조회 실패", e);
        }
    }

    /**
     * 메시지 소유권 변경 (XCLAIM)
     */
    private void claimMessage(String consumerName, String messageId) {
        try {
            // XCLAIM fcm:stream fcm-consumers server1 30000 1704000000000-0
            List<MapRecord<String, Object, Object>> claimed = redisTemplate.opsForStream().claim(
                    STREAM_KEY,
                    CONSUMER_GROUP,
                    consumerName,
                    Duration.ofMillis(CLAIM_MIN_IDLE_TIME_MS),
                    RecordId.of(messageId));

            if (claimed != null && !claimed.isEmpty()) {
                log.info("FCM 메시지 인수 완료: messageId={}, newOwner={}",
                        messageId, consumerName);
            }

        } catch (Exception e) {
            log.error("FCM 메시지 인수 실패: messageId={}", messageId, e);
        }
    }

    /**
     * Stream 크기 조회 (모니터링용)
     */
    public Long getStreamSize() {
        try {
            return redisTemplate.opsForStream().size(STREAM_KEY);
        } catch (Exception e) {
            log.error("FCM Stream 크기 조회 실패", e);
            return 0L;
        }
    }

    /**
     * DLQ 크기 조회 (모니터링용)
     */
    public Long getDlqSize() {
        try {
            return redisTemplate.opsForStream().size(DLQ_STREAM_KEY);
        } catch (Exception e) {
            log.error("FCM DLQ 크기 조회 실패", e);
            return 0L;
        }
    }
}
