package com.project.catxi.fcm.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "fcm_outbox")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FcmOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, length = 36)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private EventType eventType;

    @Column(name = "payload", nullable = false, columnDefinition = "JSON")
    @JdbcTypeCode(SqlTypes.JSON)
    private String payload;

    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private OutboxStatus status = OutboxStatus.PENDING;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Builder
    public FcmOutbox(String eventId, EventType eventType, String payload) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.payload = payload;
        this.status = OutboxStatus.PENDING;
        this.retryCount = 0;
        this.createdAt = LocalDateTime.now();
    }

    /**
     * 발행 성공 처리
     */
    public void markAsSent() {
        this.status = OutboxStatus.SENT;
        this.sentAt = LocalDateTime.now();
    }

    /**
     * 재시도 카운트 증가
     */
    public void incrementRetry() {
        this.retryCount++;
    }

    /**
     * 최종 실패 처리
     */
    public void markAsFailed(String errorMessage) {
        this.status = OutboxStatus.FAILED;
        this.errorMessage = errorMessage;
    }

    /**
     * 재시도 가능 여부 확인
     * 
     * @param maxRetry 최대 재시도 횟수
     */
    public boolean canRetry(int maxRetry) {
        return this.retryCount < maxRetry;
    }

    public enum EventType {
        CHAT_MESSAGE,
        READY_REQUEST
    }

    public enum OutboxStatus {
        PENDING, // 발행 대기
        SENT, // 발행 완료
        FAILED // 발행 실패
    }
}
