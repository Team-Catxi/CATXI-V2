package com.project.catxi.fcm.service;

import com.project.catxi.fcm.domain.FcmOutbox;
import com.project.catxi.fcm.repository.FcmOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 Outbox → Redis Streams 발행
 * - FcmOutboxPoller의 내부 호출 트랜잭션 문제 해결을 위해 별도 서비스로 분리
 * - @Transactional이 프록시를 통해 정상 동작
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FcmOutboxPublisher {

    private static final int MAX_RETRY = 3;

    private final FcmOutboxRepository fcmOutboxRepository;
    private final FcmStreamService fcmStreamService;

    /**
     * Redis Streams에 이벤트 발행 및 상태 업데이트
     * 
     * - 500ms 폴링 + 즉시 상태 변경으로 중복 발행 가능성 최소화
     * - @Transactional로 발행 후 즉시 SENT 상태 커밋
     */
    @Transactional
    public boolean publishToRedis(FcmOutbox outbox) {
        try {
            // Redis Streams에 발행 (XADD)
            String messageId = fcmStreamService.publish(outbox.getPayload());

            // 성공 시 SENT로 변경
            outbox.markAsSent();
            fcmOutboxRepository.save(outbox);

            log.debug("FCM Outbox 발행 성공: EventId={}, MessageId={}",
                    outbox.getEventId(), messageId);
            return true;

        } catch (Exception e) {
            log.error("FCM Outbox 발행 실패: EventId={}, Error={}",
                    outbox.getEventId(), e.getMessage(), e);

            // 재시도 처리
            return handleFailure(outbox, e);
        }
    }

    /**
     * 실패 처리 및 재시도 로직
     */
    private boolean handleFailure(FcmOutbox outbox, Exception e) {
        outbox.incrementRetry();

        if (outbox.canRetry(MAX_RETRY)) {
            // 재시도 가능 - PENDING 유지
            fcmOutboxRepository.save(outbox);
            log.warn("FCM Outbox 재시도 예정: EventId={}, RetryCount={}/{}",
                    outbox.getEventId(), outbox.getRetryCount(), MAX_RETRY);
            return false;
        } else {
            // 최대 재시도 초과 - FAILED로 변경
            outbox.markAsFailed(e.getMessage());
            fcmOutboxRepository.save(outbox);
            log.error("FCM Outbox 최종 실패: EventId={}, RetryCount={}",
                    outbox.getEventId(), outbox.getRetryCount());
            return false;
        }
    }
}
