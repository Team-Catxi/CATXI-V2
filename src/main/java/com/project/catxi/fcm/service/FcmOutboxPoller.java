package com.project.catxi.fcm.service;

import com.project.catxi.fcm.domain.FcmOutbox;
import com.project.catxi.fcm.domain.FcmOutbox.OutboxStatus;
import com.project.catxi.fcm.repository.FcmOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 PENDING 상태의 이벤트를 주기적으로 조회하여 Redis Streams로 발행
 * - 1초마다 실행
 * - 최대 100개씩 처리
 * - 성공 시 SENT로 상태 변경
 * - 실패 시 재시도 (최대 3회)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FcmOutboxPoller {

    private static final int BATCH_SIZE = 100;

    private final FcmOutboxRepository fcmOutboxRepository;
    private final FcmOutboxPublisher fcmOutboxPublisher;

    /**
     * Outbox 폴링 및 발행
     * - 500ms마다 실행 (fixedDelay = 500ms)
     * - PENDING 상태를 오래된 순으로 조회
     */
    @Scheduled(fixedDelay = 500)
    public void pollAndPublish() {
        try {
            // PENDING 상태 조회 (최대 100개)
            List<FcmOutbox> pendingEvents = fcmOutboxRepository.findByStatusOrderByCreatedAtAsc(
                    OutboxStatus.PENDING,
                    PageRequest.of(0, BATCH_SIZE));

            if (pendingEvents.isEmpty()) {
                return; // 처리할 이벤트 없음
            }

            log.debug("FCM Outbox 폴링: {} 개 이벤트 처리 시작", pendingEvents.size());

            int successCount = 0;
            int failCount = 0;

            for (FcmOutbox outbox : pendingEvents) {
                // 별도 서비스 호출 → 트랜잭션 정상 동작
                boolean success = fcmOutboxPublisher.publishToRedis(outbox);
                if (success) {
                    successCount++;
                } else {
                    failCount++;
                }
            }

            log.info("FCM Outbox 폴링 완료: 성공={}, 실패={}", successCount, failCount);

        } catch (Exception e) {
            log.error("FCM Outbox 폴링 중 오류 발생", e);
        }
    }

    /**
     * 모니터링: Outbox 상태 로깅
     */
    @Scheduled(fixedDelay = 60000) // 1분마다
    public void logOutboxStatus() {
        try {
            long pendingCount = fcmOutboxRepository.countByStatus(OutboxStatus.PENDING);
            long failedCount = fcmOutboxRepository.countByStatus(OutboxStatus.FAILED);

            if (pendingCount > 0 || failedCount > 0) {
                log.info("FCM Outbox 상태: PENDING={}, FAILED={}", pendingCount, failedCount);
            }

            // FAILED가 10개 이상이면 경고
            if (failedCount >= 10) {
                log.warn("⚠️ FCM Outbox FAILED 상태가 {}개입니다. 확인이 필요합니다.", failedCount);
            }

        } catch (Exception e) {
            log.error("FCM Outbox 상태 조회 중 오류", e);
        }
    }

    /**
     * 오래된 SENT 데이터 정리
     * - 매일 새벽 3시 실행
     * - 7일 이전 데이터 삭제
     */
    @Scheduled(cron = "0 0 3 * * *")
    public void cleanupSentOutbox() {
        try {
            LocalDateTime before = LocalDateTime.now().minusDays(7);
            List<FcmOutbox> oldEvents = fcmOutboxRepository.findOldSentEvents(
                    OutboxStatus.SENT, before);

            if (!oldEvents.isEmpty()) {
                fcmOutboxRepository.deleteAll(oldEvents);
                log.info("FCM Outbox 정리: {} 개의 오래된 SENT 데이터 삭제", oldEvents.size());
            }

        } catch (Exception e) {
            log.error("FCM Outbox 정리 중 오류", e);
        }
    }
}
