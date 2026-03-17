package com.project.catxi.fcm.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record FcmNotificationEvent(
        @JsonProperty("eventId") String eventId,
        @JsonProperty("type") NotificationType type,
        @JsonProperty("targetMemberIds") List<Long> targetMemberIds,
        @JsonProperty("title") String title,
        @JsonProperty("body") String body,
        @JsonProperty("data") Map<String, String> data,
        @JsonProperty("createdAt") LocalDateTime createdAt,
        @JsonProperty("retryCount") int retryCount) {

    /**
     * 채팅 메시지 알림 생성 (단일 사용자)
     */
    public static FcmNotificationEvent createChatMessage(Long targetMemberId, Long roomId, Long messageId,
            String senderNickname, String message) {
        String eventId = UUID.randomUUID().toString();

        return new FcmNotificationEvent(
                eventId,
                NotificationType.CHAT_MESSAGE,
                List.of(targetMemberId),
                "새로운 채팅 메시지",
                String.format("%s: %s", senderNickname, message),
                Map.of("type", "CHAT", "roomId", String.valueOf(roomId), "messageId", String.valueOf(messageId)),
                LocalDateTime.now(),
                0);
    }

    /**
     * 준비 요청 알림 생성 (다중 사용자)
     */
    public static FcmNotificationEvent createReadyRequest(List<Long> targetMemberIds, Long roomId) {
        String eventId = UUID.randomUUID().toString();

        return new FcmNotificationEvent(
                eventId,
                NotificationType.READY_REQUEST,
                targetMemberIds,
                "준비 요청",
                "방장이 준비요청을 보냈습니다",
                Map.of("type", "READY_REQUEST", "roomId", roomId.toString()),
                LocalDateTime.now(),
                0);
    }

    /**
     * 재시도 시 retryCount 증가
     */
    public FcmNotificationEvent withIncrementedRetry() {
        return new FcmNotificationEvent(
                this.eventId,
                this.type,
                this.targetMemberIds,
                this.title,
                this.body,
                this.data,
                this.createdAt,
                this.retryCount + 1);
    }

    public enum NotificationType {
        CHAT_MESSAGE,
        READY_REQUEST
    }
}