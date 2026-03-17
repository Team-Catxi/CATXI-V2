package com.project.catxi.fcm.repository;

import com.project.catxi.fcm.domain.FcmOutbox;
import com.project.catxi.fcm.domain.FcmOutbox.OutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface FcmOutboxRepository extends JpaRepository<FcmOutbox, Long> {

    /**
     * 상태별로 오래된 순서로 조회
     * 
     * @param status   조회할 상태 (PENDING, SENT, FAILED)
     * @param pageable 페이지네이션 (예: PageRequest.of(0, 100))
     * @return 해당 상태의 Outbox 목록
     */
    List<FcmOutbox> findByStatusOrderByCreatedAtAsc(OutboxStatus status, Pageable pageable);

    /**
     * 상태별 개수 조회 (모니터링용)
     * 
     * @param status 조회할 상태
     * @return 해당 상태의 개수
     */
    long countByStatus(OutboxStatus status);

    /**
     * 특정 시간 이전에 생성된 SENT 상태 데이터 조회 (정리용)
     * 
     * @param status 상태 (SENT)
     * @param before 기준 시간
     * @return 해당 조건의 Outbox 목록
     */
    @Query("SELECT o FROM FcmOutbox o WHERE o.status = :status AND o.createdAt < :before")
    List<FcmOutbox> findOldSentEvents(OutboxStatus status, LocalDateTime before);
}
