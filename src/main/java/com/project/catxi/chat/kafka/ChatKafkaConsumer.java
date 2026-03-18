package com.project.catxi.chat.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.catxi.chat.dto.ChatMessageSendReq;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Component;

/**
 * Phase 3 비교용 — Kafka 기반 채팅 메시지 컨슈머.
 * profile = "kafka"일 때만 활성화.
 *
 * Redis Pub/Sub 방식(RedisPubSubService.onMessage)과 달리:
 *   - 컨슈머 재시작 시 오프셋 기반으로 미처리 메시지 재처리 가능
 *   - 단, 채팅 특성상 "방금 전 메시지"만 의미 있으므로 내구성 이점이 미미함
 *   - 파티션 수 = 1이므로 순서 보장되지만 병렬처리 불가
 *
 * 비교 결론: Redis Pub/Sub 대비 지연 열세 + 운영 복잡도 증가 → 미채택
 */
@Slf4j
@Component
@Profile("kafka")
@RequiredArgsConstructor
public class ChatKafkaConsumer {

    private final SimpMessageSendingOperations messageTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Kafka 토픽 "catxi-chat"에서 메시지를 수신해 WebSocket으로 브로드캐스트.
     * Redis의 'chat' 채널 구독을 대체하는 코드.
     */
    @KafkaListener(
            topics = ChatKafkaPublisher.TOPIC,
            groupId = "catxi-chat-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onMessage(String payload) {
        try {
            ChatMessageSendReq message = objectMapper.readValue(payload, ChatMessageSendReq.class);
            messageTemplate.convertAndSend("/topic/" + message.roomId(), message);
        } catch (JsonProcessingException e) {
            log.error("[Kafka consume 실패] payload 파싱 오류 — payload={}", payload, e);
        }
    }
}
