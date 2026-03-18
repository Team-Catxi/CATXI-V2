package com.project.catxi.chat.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.catxi.chat.dto.ChatMessageSendReq;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Phase 3 비교용 — Kafka 기반 채팅 메시지 퍼블리셔.
 * profile = "kafka"일 때만 활성화.
 *
 * 현재 프로덕션 코드(StompController)는 redisTemplate.convertAndSend("chat", message)를 사용.
 * Kafka 채택 시 아래와 같이 교체하면 됨:
 *
 *   // Before (Redis)
 *   redisTemplate.convertAndSend("chat", message);
 *
 *   // After (Kafka)
 *   chatKafkaPublisher.publish(enriched);
 */
@Slf4j
@Component
@Profile("kafka")
@RequiredArgsConstructor
public class ChatKafkaPublisher {

    static final String TOPIC = "catxi-chat";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    /**
     * roomId를 파티션 키로 사용해 같은 방의 메시지 순서를 보장.
     */
    public void publish(ChatMessageSendReq message) {
        try {
            String payload = objectMapper.writeValueAsString(message);
            kafkaTemplate.send(TOPIC, String.valueOf(message.roomId()), payload);
        } catch (JsonProcessingException e) {
            log.error("[Kafka publish 실패] roomId={}", message.roomId(), e);
            throw new RuntimeException(e);
        }
    }
}
